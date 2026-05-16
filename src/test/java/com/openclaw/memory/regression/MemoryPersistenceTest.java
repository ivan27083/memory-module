package com.openclaw.memory.regression;

import com.openclaw.memory.MemoryModuleApplication;
import com.openclaw.memory.application.MemoryFacade;
import com.openclaw.memory.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression test for memory persistence functionality.
 * Tests that memories persist across sessions and proper isolation.
 * Note: This test assumes the underlying storage system provides persistence.
 */
@SpringBootTest(classes = MemoryModuleApplication.class)
@DisplayName("Memory Persistence Regression Tests")
public class MemoryPersistenceTest {

    @Autowired
    private MemoryFacade memoryFacade;

    private String agentId;
    private String sessionId;

    @BeforeEach
    void setUp() {
        agentId = "test-agent-" + System.nanoTime();
        sessionId = "test-session-" + System.nanoTime();
    }

    @Test
    @DisplayName("Memory persistence - verify data survives across sessions")
    void testMemoryPersistence_AcrossSessions() {
        // First session: write memory
        String content = "Test memory that should persist across sessions";
        MemoryRecord saved = memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                sessionId,
                MemoryType.EPISODIC,
                content,
                Map.of("test", "persistence", "session", "1")
            )
        );
        
        assertThat(saved).isNotNull();
        assertThat(saved.content()).isEqualTo(content);
        
        // Simulate session restart by creating new session ID
        String newSessionId = "new-session-" + System.nanoTime();
        
        // Second session: retrieve memory
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                newSessionId,
                "persist across sessions",
                5,
                Map.of()
            )
        );
        
        // Verify memory is still available
        assertThat(results)
            .describedAs("Memory should persist across different sessions")
            .isNotEmpty();
        assertThat(results.get(0).content()).contains("persist across sessions");
        assertThat(results.get(0).metadata()).containsKey("test");
        assertThat(results.get(0).metadata().get("test")).isEqualTo("persistence");
    }

    @Test
    @DisplayName("Memory persistence - verify agent isolation")
    void testMemoryPersistence_AgentIsolation() {
        // First agent writes memory
        String agent1Id = "agent-1-" + System.nanoTime();
        String content = "Memory written by agent 1";
        
        MemoryRecord saved = memoryFacade.write(
            new MemoryWriteCommand(
                agent1Id,
                sessionId,
                MemoryType.EPISODIC,
                content,
                Map.of("agent", "1")
            )
        );
        
        assertThat(saved).isNotNull();
        
        // Second agent should not see first agent's memories
        String agent2Id = "agent-2-" + System.nanoTime();
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agent2Id,
                sessionId,
                "Memory written by agent 1",
                5,
                Map.of()
            )
        );
        
        // Verify agent isolation
        assertThat(results)
            .describedAs("Agent 2 should not see Agent 1's memories")
            .isEmpty();
    }

    @Test
    @DisplayName("Memory persistence - verify session isolation")
    void testMemoryPersistence_SessionIsolation() {
        // First session: write memory
        String session1 = "session-1-" + System.nanoTime();
        memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                session1,
                MemoryType.EPISODIC,
                "Memory from session 1",
                Map.of("session", "1")
            )
        );
        
        // Second session: write different memory
        String session2 = "session-2-" + System.nanoTime();
        memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                session2,
                MemoryType.EPISODIC,
                "Memory from session 2",
                Map.of("session", "2")
            )
        );
        
        // Session 1 memories should be accessible from any session of same agent
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                session2,
                "session 1",
                5,
                Map.of()
            )
        );
        
        // Memories should be accessible across sessions for same agent
        assertThat(results).isNotEmpty();
    }

    @Test
    @DisplayName("Memory persistence - verify multiple memory types persistence")
    void testMemoryPersistence_MultipleMemoryTypes() {
        // Write different memory types
        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            "An episode from yesterday's meeting",
            Map.of("type", "episodic")
        ));
        
        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.SEMANTIC_WIKI,
            "The capital of France is Paris",
            Map.of("type", "semantic")
        ));
        
        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            "To make tea: boil water, add tea leaves, steep for 5 minutes",
            Map.of("type", "procedural")
        ));
        
        // New session
        String newSessionId = "new-session-" + System.nanoTime();
        
        // Retrieve memories
        List<RetrievalResult> episodicResults = memoryFacade.retrieve(
            new RetrievalQuery(agentId, newSessionId, "yesterday's meeting", 5, Map.of())
        );
        
        List<RetrievalResult> semanticResults = memoryFacade.retrieve(
            new RetrievalQuery(agentId, newSessionId, "capital of France", 5, Map.of())
        );
        
        List<RetrievalResult> proceduralResults = memoryFacade.retrieve(
            new RetrievalQuery(agentId, newSessionId, "make tea", 5, Map.of())
        );
        
        // All types should be retrievable
        assertThat(episodicResults).isNotEmpty();
        assertThat(semanticResults).isNotEmpty();
        assertThat(proceduralResults).isNotEmpty();
    }

    @Test
    @DisplayName("Memory persistence - verify metadata persistence")
    void testMemoryPersistence_MetadataPersistence() {
        // Given
        Map<String, Object> metadata = Map.of(
            "priority", "high",
            "tags", "important,urgent",
            "timestamp", System.currentTimeMillis(),
            "category", "regression"
        );
        
        // When - write with metadata
        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            "Memory with important metadata",
            metadata
        ));
        
        // Then - retrieve and verify metadata
        String newSessionId = "new-session-" + System.nanoTime();
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(agentId, newSessionId, "important metadata", 5, Map.of())
        );
        
        assertThat(results).isNotEmpty();
        RetrievalResult result = results.get(0);
        assertThat(result.metadata()).containsAllEntriesOf(metadata);
    }

    @Test
    @DisplayName("Memory persistence - verify content integrity")
    void testMemoryPersistence_ContentIntegrity() {
        // Given - content with special characters and formatting
        String specialContent = """
            Line 1: Regular text
            Line 2: Special chars !@#$%^&*()_+-=[]{}|;':",./<>?
            Line 3: Unicode 你好世界 🌍 مرحبا
            Line 4: Tabs\tand\tspaces
            """;
        
        // When
        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            specialContent,
            Map.of()
        ));
        
        // Then
        String newSessionId = "new-session-" + System.nanoTime();
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(agentId, newSessionId, "Regular text", 5, Map.of())
        );
        
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).content()).contains("Line 1: Regular text");
    }

    @Test
    @DisplayName("Memory persistence - concurrent sessions for same agent")
    void testMemoryPersistence_ConcurrentSessions() {
        // Write memories in different sessions
        String session1 = "session-1-" + System.nanoTime();
        String session2 = "session-2-" + System.nanoTime();
        
        memoryFacade.write(new MemoryWriteCommand(
            agentId, session1, MemoryType.EPISODIC,
            "Memory in session 1",
            Map.of("session", "1")
        ));
        
        memoryFacade.write(new MemoryWriteCommand(
            agentId, session2, MemoryType.EPISODIC,
            "Memory in session 2",
            Map.of("session", "2")
        ));
        
        // Both memories should be accessible from either session
        List<RetrievalResult> session1Results = memoryFacade.retrieve(
            new RetrievalQuery(agentId, session1, "session 2", 5, Map.of())
        );
        
        List<RetrievalResult> session2Results = memoryFacade.retrieve(
            new RetrievalQuery(agentId, session2, "session 1", 5, Map.of())
        );
        
        assertThat(session1Results).isNotEmpty();
        assertThat(session2Results).isNotEmpty();
    }

    @Test
    @DisplayName("Memory persistence - negative scenario with non-existent agent")
    void testMemoryPersistence_Negative_NonExistentAgent() {
        // Given
        String nonExistentAgentId = "non-existent-agent-" + System.nanoTime();
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                nonExistentAgentId,
                sessionId,
                "any query",
                5,
                Map.of()
            )
        );
        
        // Then
        assertThat(results)
            .describedAs("Non-existent agent should return no results")
            .isEmpty();
    }
}