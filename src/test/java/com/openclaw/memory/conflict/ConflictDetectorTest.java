package com.openclaw.memory.conflict;

import com.openclaw.memory.agents.conflict.ConflictDetector;
import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.model.MemoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConflictDetectorTest {

    private ConflictDetector detector;

    @BeforeEach
    void setUp() {
        detector = new ConflictDetector();
    }

    // ── No conflicts ──────────────────────────────────────────────────────────

    @Test
    void detect_shouldReturnEmpty_whenNoSubjectMetadata() {
        List<MemoryRecord> records = List.of(
                record("agent-1", null, "Paris is beautiful", 0.9),
                record("agent-1", null, "Paris is the capital", 0.8)
        );
        assertThat(detector.detect(records)).isEmpty();
    }

    @Test
    void detect_shouldReturnEmpty_whenSingleRecordPerSubject() {
        List<MemoryRecord> records = List.of(
                record("agent-1", "capital-of-France", "Paris", 0.9)
        );
        assertThat(detector.detect(records)).isEmpty();
    }

    @Test
    void detect_shouldReturnEmpty_whenSameContentDuplicates() {
        List<MemoryRecord> records = List.of(
                record("agent-1", "capital-of-France", "Paris", 0.9),
                record("agent-1", "capital-of-France", "Paris", 0.8)
        );
        assertThat(detector.detect(records)).isEmpty();
    }

    @Test
    void detect_shouldReturnEmpty_whenDifferentAgents() {
        List<MemoryRecord> records = List.of(
                record("agent-1", "capital-of-France", "Paris", 0.9),
                record("agent-2", "capital-of-France", "Lyon",  0.8)
        );
        assertThat(detector.detect(records)).isEmpty();
    }

    // ── Conflict detection ────────────────────────────────────────────────────

    @Test
    void detect_shouldFindConflict_whenSameSubjectDifferentContent() {
        List<MemoryRecord> records = List.of(
                record("agent-1", "capital-of-France", "Paris", 0.9),
                record("agent-1", "capital-of-France", "Lyon",  0.5)
        );

        List<ConflictDetector.ConflictReport> conflicts = detector.detect(records);

        assertThat(conflicts).hasSize(1);
        ConflictDetector.ConflictReport r = conflicts.get(0);
        assertThat(r.subject()).isEqualTo("capital-of-France");
        assertThat(r.agentId()).isEqualTo("agent-1");
        assertThat(r.type()).isEqualTo(ConflictDetector.ConflictType.SAME_SUBJECT_DIFFERENT_CONTENT);
    }

    @Test
    void detect_winnerShouldBeHigherConfidence() {
        UUID highConfId = UUID.randomUUID();
        UUID lowConfId  = UUID.randomUUID();

        List<MemoryRecord> records = List.of(
                recordWithId(lowConfId,  "agent-1", "capital-of-France", "Lyon",  0.3),
                recordWithId(highConfId, "agent-1", "capital-of-France", "Paris", 0.9)
        );

        ConflictDetector.ConflictReport report = detector.detect(records).get(0);
        assertThat(report.winnerId()).isEqualTo(highConfId);
        assertThat(report.loserId()).isEqualTo(lowConfId);
    }

    @Test
    void detect_tiebreakedByValidFrom_newerWins() {
        UUID olderId = UUID.randomUUID();
        UUID newerId = UUID.randomUUID();
        Instant older = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant newer = Instant.now();

        List<MemoryRecord> records = List.of(
                recordWithIdAndTime(olderId, "agent-1", "capital-of-France", "Paris", 0.7, older),
                recordWithIdAndTime(newerId, "agent-1", "capital-of-France", "Lyon",  0.7, newer)
        );

        ConflictDetector.ConflictReport report = detector.detect(records).get(0);
        assertThat(report.winnerId()).isEqualTo(newerId);
    }

    @Test
    void detect_shouldFlagConfidenceInversion_whenBothHighConf() {
        List<MemoryRecord> records = List.of(
                record("agent-1", "sky-color", "blue",  0.95),
                record("agent-1", "sky-color", "green", 0.90)
        );

        ConflictDetector.ConflictReport report = detector.detect(records).get(0);
        assertThat(report.type()).isEqualTo(ConflictDetector.ConflictType.CONFIDENCE_INVERSION);
    }

    @Test
    void detect_shouldProduceOneReportPerLoser_multipleContenders() {
        List<MemoryRecord> records = List.of(
                record("agent-1", "capital-of-France", "Paris",    0.9),
                record("agent-1", "capital-of-France", "Lyon",     0.6),
                record("agent-1", "capital-of-France", "Marseille", 0.4)
        );

        assertThat(detector.detect(records)).hasSize(2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static MemoryRecord record(String agentId, String subject, String content, double confidence) {
        return recordWithId(UUID.randomUUID(), agentId, subject, content, confidence);
    }

    private static MemoryRecord recordWithId(UUID id, String agentId, String subject,
                                              String content, double confidence) {
        return recordWithIdAndTime(id, agentId, subject, content, confidence, Instant.now());
    }

    private static MemoryRecord recordWithIdAndTime(UUID id, String agentId, String subject,
                                                     String content, double confidence, Instant validFrom) {
        var meta = subject != null
                ? Map.<String, Object>of("subject", subject, "confidence", confidence)
                : Map.<String, Object>of("confidence", confidence);

        return new MemoryRecord(id, agentId, null, MemoryType.EPISODIC,
                content, meta, validFrom, validFrom, null, null);
    }
}
