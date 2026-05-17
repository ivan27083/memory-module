package com.openclaw.memory.application;

import com.openclaw.memory.adapter.out.ranking.ScoreOnlyReranker;
import com.openclaw.memory.domain.model.*;
import com.openclaw.memory.domain.port.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for deduplication logic in RetrievalOrchestrator.
 * No Spring context — all ports are inline fakes.
 */
class RetrievalOrchestratorDeduplicationTest {

    private static final String AGENT = "agent-dedup";

    @Test
    @DisplayName("Duplicate sourceId from episodic and vector — only the higher-score entry survives")
    void retrieve_sameSourceIdFromTwoSources_keepsHigherScore() {
        UUID sharedId = UUID.randomUUID();

        MemoryRecord episodicRecord = MemoryRecord.create(
                sharedId, AGENT, null, MemoryType.EPISODIC,
                "Rome was not built in a day", Map.of(), Instant.now()
        );
        RetrievalResult vectorResult = new RetrievalResult(
                sharedId, MemoryType.EPISODIC,
                "Rome was not built in a day", 0.91, Map.of(), Instant.now()
        );

        List<RetrievalResult> results = buildOrchestrator(List.of(episodicRecord), List.of(vectorResult))
                .retrieve(new RetrievalQuery(AGENT, null, "Rome", 10, Map.of()));

        // Exactly one result for sharedId — no duplicate
        assertThat(results)
                .extracting(RetrievalResult::sourceId)
                .doesNotHaveDuplicates();

        // The vector score (0.91) wins over episodic fixed score (0.75)
        assertThat(results)
                .filteredOn(r -> sharedId.equals(r.sourceId()))
                .singleElement()
                .extracting(RetrievalResult::score)
                .isEqualTo(0.91);
    }

    @Test
    @DisplayName("Unique sourceIds from all sources — deduplication leaves all entries intact")
    void retrieve_uniqueSourceIds_allEntriesKept() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        List<MemoryRecord> episodic = List.of(
                MemoryRecord.create(id1, AGENT, null, MemoryType.EPISODIC, "Fact A", Map.of(), Instant.now()),
                MemoryRecord.create(id2, AGENT, null, MemoryType.EPISODIC, "Fact B", Map.of(), Instant.now())
        );
        RetrievalResult vectorResult = new RetrievalResult(
                id3, MemoryType.EPISODIC, "Fact C", 0.88, Map.of(), Instant.now()
        );

        List<RetrievalResult> results = buildOrchestrator(episodic, List.of(vectorResult))
                .retrieve(new RetrievalQuery(AGENT, null, "facts", 10, Map.of()));

        assertThat(results).hasSize(3);
        assertThat(results)
                .extracting(RetrievalResult::sourceId)
                .containsExactlyInAnyOrder(id1, id2, id3);
    }

    @Test
    @DisplayName("Episodic score wins when it is higher than vector score for the same sourceId")
    void retrieve_sameSourceId_episodicScoreHigher_episodicWins() {
        UUID sharedId = UUID.randomUUID();

        MemoryRecord episodicRecord = MemoryRecord.create(
                sharedId, AGENT, null, MemoryType.EPISODIC, "Some fact", Map.of(), Instant.now()
        );
        // Vector returns the same id but with a lower score
        RetrievalResult vectorResult = new RetrievalResult(
                sharedId, MemoryType.EPISODIC, "Some fact", 0.50, Map.of(), Instant.now()
        );

        List<RetrievalResult> results = buildOrchestrator(List.of(episodicRecord), List.of(vectorResult))
                .retrieve(new RetrievalQuery(AGENT, null, "fact", 10, Map.of()));

        assertThat(results)
                .filteredOn(r -> sharedId.equals(r.sourceId()))
                .singleElement()
                .extracting(RetrievalResult::score)
                .isEqualTo(0.75); // episodic fixed score
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static RetrievalOrchestrator buildOrchestrator(
            List<MemoryRecord> episodicRecords,
            List<RetrievalResult> vectorResults) {

        return new RetrievalOrchestrator(
                new EmptyWorkingMemoryStore(),
                new StubEpisodicRepo(episodicRecords),
                new EmptySemanticWikiRepo(),
                new StubVectorIndex(vectorResults),
                query -> List.of(),
                content -> List.of(0.1, 0.2, 0.3, 0.4),
                new ScoreOnlyReranker()
        );
    }

    static class EmptyWorkingMemoryStore implements WorkingMemoryStore {
        @Override public MemoryRecord save(MemoryRecord r, java.time.Duration ttl) { return r; }
        @Override public List<MemoryRecord> findRecent(String a, String s, int l) { return List.of(); }
    }

    static class StubEpisodicRepo implements EpisodicMemoryRepository {
        private final List<MemoryRecord> records;
        StubEpisodicRepo(List<MemoryRecord> records) { this.records = records; }

        @Override public MemoryRecord save(MemoryRecord r) { return r; }
        @Override public List<MemoryRecord> findRecent(String agentId, String sessionId, int limit) {
            return records.stream().filter(r -> agentId.equals(r.agentId())).toList();
        }
        @Override public int deleteOlderThan(Instant cutoff) { return 0; }
    }

    static class EmptySemanticWikiRepo implements SemanticWikiRepository {
        @Override public MemoryRecord upsert(String title, MemoryRecord r) { return r; }
        @Override public List<MemoryRecord> findRelevant(String a, String q, int l) { return List.of(); }
    }

    static class StubVectorIndex implements VectorIndex {
        private final List<RetrievalResult> results;
        StubVectorIndex(List<RetrievalResult> results) { this.results = results; }

        @Override public void ensureCollection() {}
        @Override public void upsert(VectorDocument doc) {}
        @Override public void delete(UUID id) {}
        @Override public List<RetrievalResult> search(List<Double> v, int limit, Map<String, Object> f) {
            return results;
        }
    }
}
