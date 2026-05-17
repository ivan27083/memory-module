package com.openclaw.memory.conflict;

import com.openclaw.memory.agents.conflict.ConflictDetector;
import com.openclaw.memory.agents.conflict.SubjectConflictResolver;
import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.model.MemoryType;
import com.openclaw.memory.working_memory.WorkingMemoryComposer;
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
    private SubjectConflictResolver resolver;

    @BeforeEach
    void setUp() {
        detector = new ConflictDetector();
        resolver = new SubjectConflictResolver();
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
                record("agent-1", "capital-of-France", "Paris", 0.8)   // same content
        );
        assertThat(detector.detect(records)).isEmpty();
    }

    @Test
    void detect_shouldReturnEmpty_whenDifferentAgents() {
        List<MemoryRecord> records = List.of(
                record("agent-1", "capital-of-France", "Paris", 0.9),
                record("agent-2", "capital-of-France", "Lyon",  0.8)
        );
        // Different agents → different groups → no conflict
        assertThat(detector.detect(records)).isEmpty();
    }

    // ── Conflict detection ────────────────────────────────────────────────────

    @Test
    void detect_shouldFindConflict_whenSameSubjectDifferentContent() {
        List<MemoryRecord> records = List.of(
                record("agent-1", "capital-of-France", "Paris",  0.9),
                record("agent-1", "capital-of-France", "Lyon",   0.5)
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
        UUID olderId  = UUID.randomUUID();
        UUID newerId  = UUID.randomUUID();
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
        // 1 winner, 2 losers → 2 reports
        List<MemoryRecord> records = List.of(
                record("agent-1", "capital-of-France", "Paris",   0.9),
                record("agent-1", "capital-of-France", "Lyon",    0.6),
                record("agent-1", "capital-of-France", "Marseille", 0.4)
        );

        assertThat(detector.detect(records)).hasSize(2);
    }

    // ── SubjectConflictResolver ───────────────────────────────────────────────

    @Test
    void resolver_shouldKeepHigherScorePerSubject() {
        WorkingMemoryComposer.SelectedMemory high = selectedMemory("id-1", "capital-of-France", "agent-1", 0.9);
        WorkingMemoryComposer.SelectedMemory low  = selectedMemory("id-2", "capital-of-France", "agent-1", 0.5);

        List<WorkingMemoryComposer.SelectedMemory> result = resolver.resolve(List.of(high, low), "query");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).artifactId).isEqualTo("id-1");
    }

    @Test
    void resolver_shouldPassThrough_memoriesWithoutSubject() {
        WorkingMemoryComposer.SelectedMemory noSubject = selectedMemory("id-1", null, "agent-1", 0.9);

        List<WorkingMemoryComposer.SelectedMemory> result = resolver.resolve(List.of(noSubject), "query");

        assertThat(result).hasSize(1);
    }

    @Test
    void resolver_shouldNotConflict_acrossAgents() {
        WorkingMemoryComposer.SelectedMemory a1 = selectedMemory("id-1", "sky-color", "agent-1", 0.9);
        WorkingMemoryComposer.SelectedMemory a2 = selectedMemory("id-2", "sky-color", "agent-2", 0.8);

        List<WorkingMemoryComposer.SelectedMemory> result = resolver.resolve(List.of(a1, a2), "query");

        assertThat(result).hasSize(2);
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

    private static WorkingMemoryComposer.SelectedMemory selectedMemory(
            String id, String subject, String agentId, double score) {
        var meta = new java.util.LinkedHashMap<String, Object>();
        if (subject  != null) meta.put("subject",  subject);
        if (agentId  != null) meta.put("agentId",  agentId);

        var explanation = new com.openclaw.memory.retrieval.RetrievalResult(
                id, "content", score, meta,
                new com.openclaw.memory.retrieval.RetrieverExplanation(
                        "test", List.of(), 0, 0, 0, "test"),
                null
        );

        WorkingMemoryComposer.SelectedMemory m = new WorkingMemoryComposer.SelectedMemory(
                null, score, WorkingMemoryComposer.SelectionReason.RELEVANCE_MATCH, id);
        m.content = "content";
        m.retrievalExplanation = explanation;
        return m;
    }
}
