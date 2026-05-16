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
 * Regression test for memory retrieval functionality.
 * Tests both positive and negative scenarios for retrieving stored memories.
 */
@SpringBootTest(classes = MemoryModuleApplication.class)
@DisplayName("Memory Retrieve Regression Tests")
public class MemoryRetrieveTest {

    @Autowired
    private MemoryFacade memoryFacade;

    private String agentId;
    private String sessionId;

    @BeforeEach
    void setUp() {
        agentId = "test-agent";
        sessionId = "test-session";
        
        // Setup test data
        memoryFacade.write(
            agentId,
            sessionId,
            MemoryType.EPISODIC,
            "Test memory about retrieval functionality",
            Map.of("category", "retrieval")
        );
    }

    @Test
    @DisplayName("Retrieve memory - positive scenario")
    void testRetrieveMemory_Positive() {
        // Given
        String query = "retrieval functionality";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            agentId,
            sessionId,
            query,
            5,
            Map.of()
        );

        // Then
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).content()).contains("retrieval functionality");
        assertThat(results.get(0).score()).isPositive();
    }

    @Test
    @DisplayName("Retrieve memory - negative scenario with no results")
    void testRetrieveMemory_Negative_NoResults() {
        // Given
        String query = "nonexistent memory content";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            agentId,
            sessionId,
            query,
            5,
            Map.of()
        );

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Retrieve memory - negative scenario with null query")
    void testRetrieveMemory_Negative_NullQuery() {
        // Given
        String query = null;
        
        // When & Then
        assertThatThrownBy(() -> memoryFacade.retrieve(
            agentId,
            sessionId,
            query,
            5,
            Map.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }
}