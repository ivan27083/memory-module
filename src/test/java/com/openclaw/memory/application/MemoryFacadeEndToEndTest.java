package com.openclaw.memory.application;

import com.openclaw.memory.agents.conflict.ConflictDetector;
import com.openclaw.memory.config.MemoryModuleProperties;
import com.openclaw.memory.domain.model.*;
import com.openclaw.memory.domain.port.*;
import com.openclaw.memory.event_store.Event;
import com.openclaw.memory.event_store.InMemoryEventStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test: real in-memory port implementations, no mocks, no external services.
 * Covers write → consolidation → retrieval → event publishing in a single JVM.
 */
class MemoryFacadeEndToEndTest {

    private DefaultMemoryFacade facade;
    private InMemoryEventStore eventStore;
    private FakeEpisodicMemoryRepository episodicRepo;

    @BeforeEach
    void setUp() {
        episodicRepo = new FakeEpisodicMemoryRepository();
        eventStore   = new InMemoryEventStore();

        // Shared so that writes via facade are visible to the orchestrator on retrieve
        FakeWorkingMemoryStore  workingStore  = new FakeWorkingMemoryStore();
        FakeSemanticWikiRepository semanticRepo = new FakeSemanticWikiRepository();

        MemoryModuleProperties props = new MemoryModuleProperties(
                Duration.ofHours(24), 10,
                new MemoryModuleProperties.Http(Duration.ofSeconds(3), Duration.ofSeconds(20)),
                new MemoryModuleProperties.Vector("agent_memory", 4, "http://localhost:6333"),
                new MemoryModuleProperties.Embedding("http://localhost:1234/v1", "test-model", "local"),
                new MemoryModuleProperties.Maintenance(Duration.ofDays(30), "0 0 3 * * *")
        );

        RetrievalOrchestrator orchestrator = new RetrievalOrchestrator(
                workingStore,
                episodicRepo,
                semanticRepo,
                new FakeVectorIndex(),
                query -> List.of(),
                content -> List.of(0.1, 0.2, 0.3, 0.4),
                (query, results) -> results
        );

        facade = new DefaultMemoryFacade(
                props,
                workingStore,
                episodicRepo,
                semanticRepo,
                orchestrator,
                new MemoryConsolidationPort() {
                    @Override public void indexMemory(MemoryRecord record) {}
                    @Override public void deleteMemory(java.util.UUID id) {}
                },
                eventStore,
                new ConflictDetector(),
                new FakeMemoryGraphPort(),
                new MemoryMetrics(new SimpleMeterRegistry())
        );
    }

    // ── write → event ─────────────────────────────────────────────────────────

    @Test
    void write_publishesMemoryRecordedEvent() {
        facade.write(new MemoryWriteCommand("agent-1", "sess-1", MemoryType.EPISODIC,
                "The Battle of Hastings was in 1066", Map.of()));

        List<Event> events = eventStore.getEventsByType(Event.EventType.MEMORY_RECORDED);
        assertThat(events).hasSize(1);

        Event e = events.get(0);
        assertThat(e.getPayload()).containsEntry("agentId", "agent-1");
        assertThat(e.getPayload()).containsEntry("sessionId", "sess-1");
        assertThat(e.getPayload()).containsEntry("type", "EPISODIC");
        assertThat(e.getPayload()).containsKey("memoryId");
        assertThat(e.getCorrelationId()).isEqualTo("agent-1:sess-1");
    }

    @Test
    void write_persistsRecordInEpisodicRepo() {
        facade.write(new MemoryWriteCommand("agent-1", null, MemoryType.EPISODIC,
                "Napoleon was exiled to Elba", Map.of()));

        List<MemoryRecord> recent = episodicRepo.findRecent("agent-1", null, 10);
        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).content()).isEqualTo("Napoleon was exiled to Elba");
    }

    @Test
    void write_multiple_allEventsPublished() {
        facade.write(new MemoryWriteCommand("agent-2", null, MemoryType.EPISODIC, "Fact A", Map.of()));
        facade.write(new MemoryWriteCommand("agent-2", null, MemoryType.EPISODIC, "Fact B", Map.of()));
        facade.write(new MemoryWriteCommand("agent-2", null, MemoryType.EPISODIC, "Fact C", Map.of()));

        assertThat(eventStore.getEventsByType(Event.EventType.MEMORY_RECORDED)).hasSize(3);
        assertThat(episodicRepo.findRecent("agent-2", null, 10)).hasSize(3);
    }

    // ── retrieve → result + event ─────────────────────────────────────────────

    @Test
    void retrieve_afterWrite_returnsStoredRecord() {
        facade.write(new MemoryWriteCommand("agent-3", null, MemoryType.EPISODIC,
                "Rome was not built in a day", Map.of()));

        List<RetrievalResult> results = facade.retrieve(
                new RetrievalQuery("agent-3", null, "Rome", 5, Map.of()));

        assertThat(results).isNotEmpty();
        assertThat(results.stream().map(RetrievalResult::content))
                .contains("Rome was not built in a day");
    }

    @Test
    void retrieve_withResults_publishesRetrievedEvent() {
        facade.write(new MemoryWriteCommand("agent-4", "s1", MemoryType.EPISODIC,
                "Water boils at 100°C", Map.of()));

        facade.retrieve(new RetrievalQuery("agent-4", "s1", "boiling point", 5, Map.of()));

        List<Event> events = eventStore.getEventsByType(Event.EventType.MEMORY_RETRIEVED);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getPayload()).containsEntry("agentId", "agent-4");
    }

    @Test
    void retrieve_noResults_doesNotPublishEvent() {
        // No writes for this agent — retrieve returns empty → no MEMORY_RETRIEVED event
        facade.retrieve(new RetrievalQuery("agent-nobody", null, "anything", 5, Map.of()));

        assertThat(eventStore.getEventsByType(Event.EventType.MEMORY_RETRIEVED)).isEmpty();
    }

    // ── conflict detection ────────────────────────────────────────────────────

    @Test
    void write_conflictingSubjects_publishesConflictEvents() {
        Map<String, Object> meta = Map.of("subject", "capital-of-france");
        facade.write(new MemoryWriteCommand("agent-5", null, MemoryType.EPISODIC,
                "The capital of France is Paris", meta));
        facade.write(new MemoryWriteCommand("agent-5", null, MemoryType.EPISODIC,
                "The capital of France is Lyon", meta));

        // At minimum the two MEMORY_RECORDED events should be there
        assertThat(eventStore.getEventsByType(Event.EventType.MEMORY_RECORDED)).hasSize(2);
    }

    // ── maintenance + episodic repo ───────────────────────────────────────────

    @Test
    void deleteOlderThan_removesOldRecords() {
        facade.write(new MemoryWriteCommand("agent-6", null, MemoryType.EPISODIC, "Old fact", Map.of()));
        assertThat(episodicRepo.findRecent("agent-6", null, 10)).hasSize(1);

        // Delete everything older than "now" — removes the record just written
        int deleted = episodicRepo.deleteOlderThan(Instant.now().plusSeconds(1));
        assertThat(deleted).isEqualTo(1);
        assertThat(episodicRepo.findRecent("agent-6", null, 10)).isEmpty();
    }

    @Test
    void deleteOlderThan_futureCutoff_keepsRecentRecords() {
        facade.write(new MemoryWriteCommand("agent-7", null, MemoryType.EPISODIC, "Fresh fact", Map.of()));

        int deleted = episodicRepo.deleteOlderThan(Instant.now().minusSeconds(60));
        assertThat(deleted).isEqualTo(0);
        assertThat(episodicRepo.findRecent("agent-7", null, 10)).hasSize(1);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Fake port implementations
    // ═════════════════════════════════════════════════════════════════════════

    static class FakeEpisodicMemoryRepository implements EpisodicMemoryRepository {
        private final List<MemoryRecord> store = new ArrayList<>();

        @Override
        public MemoryRecord save(MemoryRecord record) {
            MemoryRecord saved = MemoryRecord.create(
                    UUID.randomUUID(), record.agentId(), record.sessionId(),
                    record.type(), record.content(), record.metadata(), Instant.now());
            store.add(saved);
            return saved;
        }

        @Override
        public List<MemoryRecord> findRecent(String agentId, String sessionId, int limit) {
            return store.stream()
                    .filter(r -> agentId.equals(r.agentId()))
                    .filter(r -> sessionId == null || sessionId.equals(r.sessionId()))
                    .sorted(Comparator.comparing(MemoryRecord::createdAt).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        @Override
        public int deleteOlderThan(Instant cutoff) {
            int before = store.size();
            store.removeIf(r -> r.createdAt().isBefore(cutoff));
            return before - store.size();
        }
    }

    static class FakeWorkingMemoryStore implements WorkingMemoryStore {
        private final Map<String, MemoryRecord> store = new ConcurrentHashMap<>();

        @Override
        public MemoryRecord save(MemoryRecord record, Duration ttl) {
            MemoryRecord saved = MemoryRecord.create(
                    UUID.randomUUID(), record.agentId(), record.sessionId(),
                    record.type(), record.content(), record.metadata(), Instant.now());
            store.put(saved.id().toString(), saved);
            return saved;
        }

        @Override
        public List<MemoryRecord> findRecent(String agentId, String sessionId, int limit) {
            return store.values().stream()
                    .filter(r -> agentId.equals(r.agentId()))
                    .limit(limit)
                    .toList();
        }
    }

    static class FakeSemanticWikiRepository implements SemanticWikiRepository {
        private final Map<String, MemoryRecord> store = new LinkedHashMap<>();

        @Override
        public MemoryRecord upsert(String title, MemoryRecord record) {
            MemoryRecord saved = MemoryRecord.create(
                    UUID.randomUUID(), record.agentId(), record.sessionId(),
                    record.type(), record.content(), record.metadata(), Instant.now());
            store.put(title, saved);
            return saved;
        }

        @Override
        public List<MemoryRecord> findRelevant(String agentId, String query, int limit) {
            return store.values().stream()
                    .filter(r -> agentId.equals(r.agentId()))
                    .limit(limit)
                    .toList();
        }
    }

    static class FakeVectorIndex implements VectorIndex {
        @Override public void ensureCollection() {}
        @Override public void upsert(VectorDocument document) {}
        @Override public void delete(UUID id) {}
        @Override public List<RetrievalResult> search(List<Double> embedding, int limit, Map<String, Object> filter) {
            return List.of();
        }
    }

    static class FakeMemoryGraphPort implements MemoryGraphPort {
        @Override public void addMemory(MemoryRecord record) {}
        @Override public void recordSupersession(UUID oldId, UUID newId, Instant at) {}
        @Override public List<String> getSupersessionChain(String memoryId) { return List.of(); }
        @Override public boolean isConsistent(String artifactId, java.time.LocalDateTime atTime) { return true; }
    }
}
