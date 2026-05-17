package com.openclaw.memory.event_store;

import com.openclaw.memory.agents.conflict.ConflictDetector;
import com.openclaw.memory.application.DefaultMemoryFacade;
import com.openclaw.memory.application.RetrievalOrchestrator;
import com.openclaw.memory.config.MemoryModuleProperties;
import com.openclaw.memory.domain.model.*;
import com.openclaw.memory.domain.port.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EventStoreIntegrationTest {

    private InMemoryEventStore eventStore;
    private DefaultMemoryFacade facade;

    @BeforeEach
    void setUp() {
        eventStore = new InMemoryEventStore();

        // ── Port stubs ────────────────────────────────────────────────────────
        EpisodicMemoryRepository episodic = mock(EpisodicMemoryRepository.class);
        when(episodic.save(any())).thenAnswer(inv -> {
            MemoryRecord r = inv.getArgument(0);
            return MemoryRecord.create(UUID.randomUUID(), r.agentId(), r.sessionId(),
                    r.type(), r.content(), r.metadata(), Instant.now());
        });

        WorkingMemoryStore working = mock(WorkingMemoryStore.class);
        SemanticWikiRepository semantic = mock(SemanticWikiRepository.class);

        MemoryConsolidationPort consolidation = mock(MemoryConsolidationPort.class);
        doNothing().when(consolidation).indexMemory(any());

        EmbeddingClient embedder = mock(EmbeddingClient.class);
        when(embedder.embed(any())).thenReturn(List.of(0.1, 0.2));

        VectorIndex vectorIndex = mock(VectorIndex.class);
        ExternalKnowledgeRetriever external = mock(ExternalKnowledgeRetriever.class);
        when(external.retrieve(any())).thenReturn(List.of());

        Reranker reranker = mock(Reranker.class);
        when(reranker.rerank(any(), any())).thenAnswer(inv -> inv.getArgument(1));

        RetrievalOrchestrator orchestrator = new RetrievalOrchestrator(
                working, episodic, semantic, vectorIndex, external, embedder, reranker);

        MemoryModuleProperties props = new MemoryModuleProperties(
                Duration.ofHours(24), 12,
                new MemoryModuleProperties.Http(Duration.ofSeconds(3), Duration.ofSeconds(20)),
                new MemoryModuleProperties.Vector("agent_memory", 768, "http://localhost:6333"),
                new MemoryModuleProperties.Embedding("http://localhost:1234/v1",
                        "text-embedding-test", "local"),
                new MemoryModuleProperties.Maintenance(Duration.ofDays(30), "0 0 3 * * *"));

        com.openclaw.memory.domain.port.MemoryGraphPort graphPort =
                mock(com.openclaw.memory.domain.port.MemoryGraphPort.class);
        com.openclaw.memory.application.MemoryMetrics metrics =
                new com.openclaw.memory.application.MemoryMetrics(
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        facade = new DefaultMemoryFacade(
                props, working, episodic, semantic, orchestrator, consolidation,
                eventStore, new ConflictDetector(), graphPort, metrics);
    }

    // ── write() emits MEMORY_RECORDED ────────────────────────────────────────

    @Test
    void write_shouldPublishMemoryRecordedEvent() {
        facade.write(new MemoryWriteCommand(
                "agent-1", "session-42", MemoryType.EPISODIC,
                "Claude invented something.", Map.of()));

        List<Event> events = eventStore.getEventsByType(Event.EventType.MEMORY_RECORDED);
        assertThat(events).hasSize(1);

        Event e = events.get(0);
        assertThat(e.getSourceAgent()).isEqualTo("DefaultMemoryFacade");
        assertThat(e.getPayload()).containsEntry("agentId", "agent-1");
        assertThat(e.getPayload()).containsEntry("sessionId", "session-42");
        assertThat(e.getPayload()).containsKey("memoryId");
        assertThat(e.getPayload()).containsEntry("type", "EPISODIC");
        assertThat(e.getCorrelationId()).isEqualTo("agent-1:session-42");
    }

    @Test
    void write_multipleRecords_shouldEmitOneEventEach() {
        facade.write(new MemoryWriteCommand("agent-1", null, MemoryType.EPISODIC, "First.", Map.of()));
        facade.write(new MemoryWriteCommand("agent-1", null, MemoryType.EPISODIC, "Second.", Map.of()));

        assertThat(eventStore.getEventsByType(Event.EventType.MEMORY_RECORDED)).hasSize(2);
        assertThat(eventStore.getEventCount()).isEqualTo(2);
    }

    @Test
    void write_shouldIncludeContentPreviewInPayload() {
        String longContent = "A".repeat(200);
        facade.write(new MemoryWriteCommand("agent-1", null, MemoryType.EPISODIC, longContent, Map.of()));

        Event e = eventStore.getEventsByType(Event.EventType.MEMORY_RECORDED).get(0);
        String preview = (String) e.getPayload().get("contentPreview");
        assertThat(preview).isNotNull().hasSizeLessThanOrEqualTo(121); // 120 chars + ellipsis
    }

    // ── retrieve() emits MEMORY_RETRIEVED ────────────────────────────────────

    @Test
    void retrieve_withEmptyResults_shouldNotPublishEvent() {
        // All port stubs return empty lists → orchestrator returns empty → no event
        facade.retrieve(new RetrievalQuery("agent-1", "session-42", "what did Claude invent?", 5, Map.of()));

        assertThat(eventStore.getEventsByType(Event.EventType.MEMORY_RETRIEVED)).isEmpty();
    }

    // ── EventStore itself ─────────────────────────────────────────────────────

    @Test
    void eventStore_appendTwice_shouldThrowOnDuplicateId() {
        Event e = new Event.Builder()
                .eventId("dup-1")
                .sourceAgent("test")
                .eventType(Event.EventType.SYSTEM_EVENT)
                .build();

        eventStore.appendEvent(e);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> eventStore.appendEvent(e)
        );
    }

    @Test
    void eventStore_getEventsByType_shouldReturnOnlyMatchingType() {
        facade.write(new MemoryWriteCommand("a1", null, MemoryType.EPISODIC, "x", Map.of()));
        facade.write(new MemoryWriteCommand("a2", null, MemoryType.EPISODIC, "y", Map.of()));

        assertThat(eventStore.getEventsByType(Event.EventType.MEMORY_RECORDED)).hasSize(2);
        assertThat(eventStore.getEventsByType(Event.EventType.MEMORY_RETRIEVED)).isEmpty();
        assertThat(eventStore.getEventsByType(Event.EventType.CONFLICT_DETECTED)).isEmpty();
    }

    @Test
    void eventStore_correlationId_groupsRelatedEvents() {
        facade.write(new MemoryWriteCommand("agent-X", "sess-1", MemoryType.EPISODIC, "a", Map.of()));
        facade.write(new MemoryWriteCommand("agent-X", "sess-1", MemoryType.EPISODIC, "b", Map.of()));
        facade.write(new MemoryWriteCommand("agent-X", "sess-2", MemoryType.EPISODIC, "c", Map.of()));

        assertThat(eventStore.getEventsByCorrelation("agent-X:sess-1")).hasSize(2);
        assertThat(eventStore.getEventsByCorrelation("agent-X:sess-2")).hasSize(1);
    }
}
