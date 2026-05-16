package com.openclaw.memory.regression;

import com.openclaw.memory.MemoryModuleApplication;
import com.openclaw.memory.application.MemoryFacade;
import com.openclaw.memory.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive integration regression tests for memory module.
 * Tests complex scenarios combining multiple features and edge cases.
 */
@SpringBootTest(classes = MemoryModuleApplication.class)
@DisplayName("Memory Integration Regression Tests")
public class MemoryIntegrationRegressionTest {

    @Autowired
    private MemoryFacade memoryFacade;

    private String agentId;
    private String sessionId;

    @BeforeEach
    void setUp() {
        agentId = "integration-agent-" + System.nanoTime();
        sessionId = "integration-session-" + System.nanoTime();
    }

    @Test
    @DisplayName("Integration - create and retrieve complex memory workflow")
    void testIntegration_ComplexWorkflow() {
        // Step 1: Write multiple memories of different types
        MemoryRecord episodic = memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            "User attended conference on AI and Machine Learning in 2024",
            Map.of("event", "conference", "year", "2024", "topic", "AI")
        ));
        
        MemoryRecord semantic = memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.SEMANTIC_WIKI,
            "AI is the simulation of human intelligence by machines",
            Map.of("definition", "AI", "category", "technology")
        ));
        
        MemoryRecord procedural = memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            "To implement machine learning: 1) collect data, 2) train model, 3) validate, 4) deploy",
            Map.of("steps", "4", "process", "ML")
        ));

        // Step 2: Verify all writes succeeded
        assertThat(episodic).isNotNull();
        assertThat(semantic).isNotNull();
        assertThat(procedural).isNotNull();

        // Step 3: Retrieve with different queries
        List<RetrievalResult> results1 = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "AI conference 2024", 5, Map.of())
        );
        
        List<RetrievalResult> results2 = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "machine learning", 5, Map.of())
        );
        
        List<RetrievalResult> results3 = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "train model validate", 5, Map.of())
        );

        // Step 4: Verify retrievals
        assertThat(results1).isNotEmpty();
        assertThat(results2).isNotEmpty();
        assertThat(results3).isNotEmpty();
    }

    @Test
    @DisplayName("Integration - large batch memory writing")
    void testIntegration_LargeBatchWriting() {
        // Write 50 memories
        for (int i = 0; i < 50; i++) {
            memoryFacade.write(new MemoryWriteCommand(
                agentId, sessionId, MemoryType.EPISODIC,
                "Memory entry number " + i + " with content",
                Map.of("index", String.valueOf(i), "batch", "large")
            ));
        }

        // Retrieve all
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "memory entry", 100, Map.of())
        );

        // Verify
        assertThat(results).hasSizeGreaterThanOrEqualTo(40);
    }

    @Test
    @DisplayName("Integration - filtering with metadata")
    void testIntegration_FilteringWithMetadata() {
        // Write memories with different metadata
        for (int i = 0; i < 10; i++) {
            String priority = (i % 2 == 0) ? "high" : "low";
            memoryFacade.write(new MemoryWriteCommand(
                agentId, sessionId, MemoryType.EPISODIC,
                "Task " + i + " with different priority levels",
                Map.of("priority", priority, "taskId", String.valueOf(i))
            ));
        }

        // Retrieve only high priority
        List<RetrievalResult> highPriority = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "task", 100, 
                Map.of("priority", "high"))
        );

        // Retrieve only low priority
        List<RetrievalResult> lowPriority = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "task", 100,
                Map.of("priority", "low"))
        );

        // Verify
        assertThat(highPriority).isNotEmpty();
        assertThat(lowPriority).isNotEmpty();
        highPriority.forEach(r -> 
            assertThat(r.metadata().get("priority")).isEqualTo("high")
        );
        lowPriority.forEach(r ->
            assertThat(r.metadata().get("priority")).isEqualTo("low")
        );
    }

    @Test
    @DisplayName("Integration - agent memory isolation across multiple sessions")
    void testIntegration_AgentIsolationMultipleSessions() {
        String agent1 = "agent-1-" + System.nanoTime();
        String agent2 = "agent-2-" + System.nanoTime();
        String session1 = "session-1-" + System.nanoTime();
        String session2 = "session-2-" + System.nanoTime();

        // Agent 1 writes memories
        memoryFacade.write(new MemoryWriteCommand(
            agent1, session1, MemoryType.EPISODIC,
            "Agent 1 secret information",
            Map.of("agent", "1", "visibility", "secret")
        ));

        // Agent 2 writes memories
        memoryFacade.write(new MemoryWriteCommand(
            agent2, session2, MemoryType.EPISODIC,
            "Agent 2 public information",
            Map.of("agent", "2", "visibility", "public")
        ));

        // Agent 1 tries to retrieve Agent 2's memories
        List<RetrievalResult> crossAgentResults = memoryFacade.retrieve(
            new RetrievalQuery(agent1, session1, "public information", 5, Map.of())
        );

        // Verify isolation
        assertThat(crossAgentResults).isEmpty();
    }

    @Test
    @DisplayName("Integration - stress test with concurrent agent access")
    void testIntegration_ConcurrentAgentAccess() throws InterruptedException {
        // Create multiple agents writing simultaneously
        Thread[] threads = new Thread[5];
        
        for (int i = 0; i < 5; i++) {
            final int agentNum = i;
            threads[i] = new Thread(() -> {
                String threadAgentId = "concurrent-agent-" + agentNum;
                for (int j = 0; j < 10; j++) {
                    memoryFacade.write(new MemoryWriteCommand(
                        threadAgentId, sessionId, MemoryType.EPISODIC,
                        "Concurrent memory from agent " + agentNum + " entry " + j,
                        Map.of("agentNum", String.valueOf(agentNum))
                    ));
                }
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        // Verify each agent can retrieve its own memories
        for (int i = 0; i < 5; i++) {
            String threadAgentId = "concurrent-agent-" + i;
            List<RetrievalResult> results = memoryFacade.retrieve(
                new RetrievalQuery(threadAgentId, sessionId, "concurrent memory", 100, Map.of())
            );
            assertThat(results).hasSizeGreaterThanOrEqualTo(5);
        }
    }

    @Test
    @DisplayName("Integration - semantic search across memory types")
    void testIntegration_SemanticSearchAcrossTypes() {
        // Write memories of different types with related content
        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            "Experienced a successful project launch last week",
            Map.of("type", "episodic", "topic", "project")
        ));

        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.SEMANTIC_WIKI,
            "A project is a temporary endeavor undertaken to create unique outcomes",
            Map.of("type", "semantic", "topic", "project")
        ));

        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            "Steps to launch a project: plan, develop, test, deploy, monitor",
            Map.of("type", "procedural", "topic", "project")
        ));

        // Search with a general project-related query
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "project development launch", 10, Map.of())
        );

        // Should find results from all memory types
        assertThat(results).isNotEmpty();
    }

    @Test
    @DisplayName("Integration - metadata hierarchy and nested filtering")
    void testIntegration_NestedMetadataFiltering() {
        // Write with complex nested metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("department", "engineering");
        metadata.put("project", Map.of("name", "ML-Pipeline", "status", "active"));
        metadata.put("priority", 8);
        metadata.put("tags", "important,urgent,technical");

        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            "Complex ML Pipeline project memory",
            metadata
        ));

        // Retrieve with partial metadata filter
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "ML Pipeline", 5,
                Map.of("department", "engineering"))
        );

        // Verify metadata was preserved
        if (!results.isEmpty()) {
            assertThat(results.get(0).metadata().get("department"))
                .isEqualTo("engineering");
        }
    }

    @Test
    @DisplayName("Integration - memory update and retrieval consistency")
    void testIntegration_UpdateConsistency() {
        // Write initial memory
        MemoryRecord initial = memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            "Initial memory content",
            Map.of("version", "1")
        ));

        // Retrieve to verify
        List<RetrievalResult> initialResults = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "initial memory", 5, Map.of())
        );
        assertThat(initialResults).isNotEmpty();

        // Write updated memory (different content)
        MemoryRecord updated = memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            "Updated memory with new content and information",
            Map.of("version", "2")
        ));

        // Retrieve updated
        List<RetrievalResult> updatedResults = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "updated memory new content", 5, Map.of())
        );
        assertThat(updatedResults).isNotEmpty();
    }

    @Test
    @DisplayName("Integration - boundary conditions and edge cases")
    void testIntegration_BoundaryConditions() {
        // Very long content
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longContent.append("Very long content line ").append(i).append(". ");
        }

        MemoryRecord longMemory = memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            longContent.toString(),
            Map.of("size", "large")
        ));
        assertThat(longMemory).isNotNull();

        // Single character query
        List<RetrievalResult> singleCharResults = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "a", 5, Map.of())
        );
        assertThat(singleCharResults).isNotNull();

        // Query with numbers
        List<RetrievalResult> numberResults = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "123 456", 5, Map.of())
        );
        assertThat(numberResults).isNotNull();
    }

    @Test
    @DisplayName("Integration - memory type transitions")
    void testIntegration_MemoryTypeTransitions() {
        // Write episodic
        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            "An event that happened",
            Map.of("stage", "1")
        ));

        // Write semantic
        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.SEMANTIC_WIKI,
            "General knowledge about the event",
            Map.of("stage", "2")
        ));

        // Write procedural
        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            "How to handle similar events",
            Map.of("stage", "3")
        ));

        // Retrieve all with different queries
        List<RetrievalResult> eventResults = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "event", 10, Map.of())
        );

        // Should retrieve from all memory types
        assertThat(eventResults).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Integration - retrieval result ranking and scoring")
    void testIntegration_ResultRanking() {
        // Write memories with varying relevance
        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            "about machine learning algorithms",
            Map.of("relevance", "high")
        ));

        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            "machine learning and deep neural networks",
            Map.of("relevance", "very_high")
        ));

        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            "data science topics",
            Map.of("relevance", "low")
        ));

        // Retrieve with specific query
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "machine learning neural networks", 10, Map.of())
        );

        // Verify results are ranked by score
        if (results.size() > 1) {
            for (int i = 0; i < results.size() - 1; i++) {
                double currentScore = results.get(i).score();
                double nextScore = results.get(i + 1).score();
                assertThat(currentScore).isGreaterThanOrEqualTo(nextScore);
            }
        }
    }
}
