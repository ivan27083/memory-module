package com.openclaw.memory.regression;

import com.openclaw.memory.MemoryModuleApplication;
import com.openclaw.memory.application.MemoryFacade;
import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.model.RetrievalResult;
import com.openclaw.memory.domain.model.MemoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for memory persistence functionality.
 * Tests that memories persist across sessions.
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
        agentId = "test-agent";
        sessionId = "test-session";
    }

    @Test
    @DisplayName("Memory persistence - verify data survives session")
    void testMemoryPersistence_AcrossSessions() {
        // First session: write memory
        String content = "Test memory that should persist across sessions";
        MemoryRecord saved = memoryFacade.write(
            agentId,
            sessionId,
            MemoryType.EPISODIC,
            content,
            Map.of("test", "persistence")
        );
        
        assertThat(saved).isNotNull();
        assertThat(saved.content()).isEqualTo(content);
        
        // Simulate session restart by creating new session ID
        String newSessionId = "new-session";
        
        // Second session: retrieve memory
        List<RetrievalResult> results = memoryFacade.retrieve(
            agentId,
            newSessionId,
            "persist across sessions",
            5,
            Map.of()
        );
        
        // Verify memory is still available
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).content()).contains("persist across sessions");
        assertThat(results.get(0).metadata()).containsKey("test");
        assertThat(results.get(0).metadata().get("test")).isEqualTo("persistence");
    }

    @Test
    @DisplayName("Memory persistence - verify agent isolation")
    void testMemoryPersistence_AgentIsolation() {
        // First agent writes memory
        String agent1Id = "agent-1";
        String content = "Memory written by agent 1";
        
        memoryFacade.write(
            agent1Id,
            sessionId,
            MemoryType.EPISODIC,
            content,
            Map.of("agent", "1")
        );
        
        // Second agent should not see first agent's memories
        String agent2Id = "agent-2";
        List<RetrievalResult> results = memoryFacade.retrieve(
            agent2Id,
            sessionId,
            "Memory written by agent 1",
            5,
            Map.of()
        );
        
        // Verify agent isolation
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Memory persistence - negative scenario with non-existent agent")
    void testMemoryPersistence_Negative_NonExistentAgent() {
        // Given
        String nonExistentAgentId = "non-existent-agent";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            nonExistentAgentId,
            sessionId,
            "any query",
            5,
            Map.of()
        );
        
        // Then
        assertThat(results).isEmpty();
    }
}