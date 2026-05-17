package com.openclaw.memory.regression;

import com.openclaw.memory.MemoryModuleApplication;
import com.openclaw.memory.application.MemoryFacade;
import com.openclaw.memory.application.RagIngestionService;
import com.openclaw.memory.domain.model.*;
import com.openclaw.memory.domain.port.SemanticWikiRepository;
import com.openclaw.memory.event_store.Event;
import com.openclaw.memory.event_store.EventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MemoryModuleApplication.class)
@DisplayName("Advanced Memory Regression Tests")
public class MemoryAdvancedRegressionTest {

    @Autowired
    private MemoryFacade memoryFacade;

    @Autowired
    private RagIngestionService ragIngestionService;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private SemanticWikiRepository semanticWikiRepository;

    private String agentId;
    private String sessionId;

    @BeforeEach
    void setUp() {
        agentId = "adv-agent-" + System.nanoTime();
        sessionId = "adv-session-" + System.nanoTime();
    }

    @Test
    @DisplayName("RAG: ingested document is retrievable as EXTERNAL_RAG source")
    void testRag_IngestThenRetrieve() {
        String content = "Spring Boot simplifies Java application development with auto-configuration and embedded servers";
        ragIngestionService.ingest(agentId, "docs", "Spring Boot Guide", content, Map.of());

        List<RetrievalResult> results = memoryFacade.retrieve(
                new RetrievalQuery(agentId, sessionId, "Spring Boot auto-configuration", 5, Map.of())
        );

        assertThat(results)
                .describedAs("Ingested RAG document must be retrievable as EXTERNAL_RAG")
                .anyMatch(r -> r.sourceType() == MemoryType.EXTERNAL_RAG
                        && r.content().contains("Spring Boot"));
    }

    @Test
    @DisplayName("Supersession: second EPISODIC write with the same subject hides the first in all sources")
    void testSupersession_SecondWriteHidesFirst() {
        String subject = "supersession-subject-" + System.nanoTime();

        memoryFacade.write(new MemoryWriteCommand(
                agentId, sessionId, MemoryType.EPISODIC,
                "It is sunny today",
                Map.of("subject", subject)
        ));

        memoryFacade.write(new MemoryWriteCommand(
                agentId, sessionId, MemoryType.EPISODIC,
                "It is rainy today",
                Map.of("subject", subject)
        ));

        // Consolidation is now synchronous: superseded vector is deleted from Qdrant before
        // the new one is indexed, so the facade sees consistent results across all sources.
        List<RetrievalResult> results = memoryFacade.retrieve(
                new RetrievalQuery(agentId, null, "weather today", 10, Map.of())
        );

        assertThat(results)
                .describedAs("Superseded record must not appear in any retrieval source")
                .noneMatch(r -> "It is sunny today".equals(r.content()));
        assertThat(results)
                .anyMatch(r -> r.content().contains("rainy"));
    }

    @Test
    @DisplayName("SEMANTIC_WIKI: two writes with the same title produce a single updated record")
    void testSemanticWiki_UpsertDeduplication() {
        String title = "dedup-topic-" + System.nanoTime();

        memoryFacade.write(new MemoryWriteCommand(
                agentId, sessionId, MemoryType.SEMANTIC_WIKI,
                "First version of the content",
                Map.of("title", title)
        ));

        memoryFacade.write(new MemoryWriteCommand(
                agentId, sessionId, MemoryType.SEMANTIC_WIKI,
                "Updated version of the content",
                Map.of("title", title)
        ));

        List<MemoryRecord> records = semanticWikiRepository.findRelevant(agentId, title, 10);

        long countForTitle = records.stream()
                .filter(r -> title.equals(r.metadata().get("title")))
                .count();
        assertThat(countForTitle)
                .describedAs("ON CONFLICT DO UPDATE must yield exactly one record per agent+title")
                .isEqualTo(1);
        assertThat(records)
                .anyMatch(r -> title.equals(r.metadata().get("title"))
                        && r.content().contains("Updated version"));
    }

    @Test
    @DisplayName("EventStore: MEMORY_RECORDED event is published after every write")
    void testEventStore_MemoryRecordedPublished() {
        memoryFacade.write(new MemoryWriteCommand(
                agentId, sessionId, MemoryType.EPISODIC,
                "Content for event-store audit",
                Map.of()
        ));

        // correlationId for MEMORY_RECORDED = agentId + ":" + sessionId
        String correlationId = agentId + ":" + sessionId;
        List<Event> events = eventStore.getEventsByCorrelation(correlationId);

        assertThat(events)
                .describedAs("MEMORY_RECORDED must be published with correlationId=agentId:sessionId")
                .anyMatch(e -> e.getEventType() == Event.EventType.MEMORY_RECORDED
                        && agentId.equals(e.getPayload().get("agentId")));
    }

    @Test
    @DisplayName("WORKING memory: stored entry is retrievable within the same session")
    void testWorkingMemory_RetrievableInSession() {
        String marker = "working-marker-" + System.nanoTime();

        memoryFacade.write(new MemoryWriteCommand(
                agentId, sessionId, MemoryType.WORKING,
                marker,
                Map.of()
        ));

        List<RetrievalResult> results = memoryFacade.retrieve(
                new RetrievalQuery(agentId, sessionId, marker, 5, Map.of())
        );

        assertThat(results)
                .describedAs("WORKING memory must appear in results for the same session")
                .anyMatch(r -> r.sourceType() == MemoryType.WORKING
                        && r.content().contains(marker));
    }

    @Test
    @DisplayName("Conflict detection: two EPISODIC entries with the same subject trigger CONFLICT_DETECTED")
    void testConflictDetector_PublishesConflictEvent() {
        String subject = "conflict-subject-" + System.nanoTime();

        // Write first belief
        memoryFacade.write(new MemoryWriteCommand(
                agentId, sessionId, MemoryType.EPISODIC,
                "The sky is blue",
                Map.of("subject", subject, "confidence", 0.6)
        ));

        // Write conflicting belief in the same session so preloadSubjectGroup finds the first
        memoryFacade.write(new MemoryWriteCommand(
                agentId, sessionId, MemoryType.EPISODIC,
                "The sky is green",
                Map.of("subject", subject, "confidence", 0.6)
        ));

        // CONFLICT_DETECTED correlationId = report.agentId() (just agentId, no sessionId)
        List<Event> events = eventStore.getEventsByCorrelation(agentId);

        assertThat(events)
                .describedAs("CONFLICT_DETECTED must be published when two entries share the same subject with different content")
                .anyMatch(e -> e.getEventType() == Event.EventType.CONFLICT_DETECTED
                        && subject.equals(e.getPayload().get("subject")));
    }
}
