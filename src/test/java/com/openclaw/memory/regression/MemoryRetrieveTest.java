package com.openclaw.memory.regression;

import com.openclaw.memory.MemoryModuleApplication;
import com.openclaw.memory.application.MemoryFacade;
import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.model.MemoryWriteCommand;
import com.openclaw.memory.domain.model.RetrievalQuery;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression test for memory retrieval functionality.
 * Tests positive, negative, and edge case scenarios for retrieving stored memories.
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
        agentId = "test-agent-" + System.nanoTime();
        sessionId = "test-session-" + System.nanoTime();
        
        // Setup test data with various memory types
        memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                sessionId,
                MemoryType.EPISODIC,
                "Test memory about retrieval functionality for agents",
                Map.of("category", "retrieval", "type", "core")
            )
        );
        
        memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                sessionId,
                MemoryType.SEMANTIC_WIKI,
                "Information about memory systems and their components",
                Map.of("category", "semantic", "type", "reference")
            )
        );
        
        memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                sessionId,
                MemoryType.EPISODIC,
                "Steps to retrieve memories from the system",
                Map.of("category", "retrieval", "type", "procedure")
            )
        );
    }

    @Test
    @DisplayName("Retrieve memory - positive scenario exact match")
    void testRetrieveMemory_Positive_ExactMatch() {
        // Given
        String query = "retrieval functionality";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of()
            )
        );

        // Then
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).content()).contains("retrieval functionality");
        assertThat(results.get(0).score()).isPositive();
    }

    @Test
    @DisplayName("Retrieve memory - positive scenario semantic match")
    void testRetrieveMemory_Positive_SemanticMatch() {
        // Given
        String query = "memory systems";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of()
            )
        );

        // Then
        assertThat(results)
            .describedAs("Should find semantic memory matches")
            .isNotEmpty();
    }

    @Test
    @DisplayName("Retrieve memory - limit results")
    void testRetrieveMemory_Positive_LimitResults() {
        // Given
        String query = "memory";
        int limit = 2;
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                limit,
                Map.of()
            )
        );

        // Then
        assertThat(results).hasSizeLessThanOrEqualTo(limit);
    }

    @Test
    @DisplayName("Retrieve memory - negative scenario with no results")
    void testRetrieveMemory_Negative_NoResults() {
        // Given
        String query = "nonexistent_unique_memory_content_xyz123";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of()
            )
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
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of()
            )
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Retrieve memory - negative scenario with empty query")
    void testRetrieveMemory_Negative_EmptyQuery() {
        // Given
        String query = "";
        
        // When & Then
        assertThatThrownBy(() -> memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of()
            )
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Retrieve memory - negative scenario with invalid limit")
    void testRetrieveMemory_Negative_InvalidLimit() {
        // Given
        String query = "memory";
        int invalidLimit = -1;
        
        // When & Then
        assertThatThrownBy(() -> memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                invalidLimit,
                Map.of()
            )
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Retrieve memory - with filters")
    void testRetrieveMemory_Positive_WithFilters() {
        // Given
        String query = "retrieval";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of("category", "retrieval")
            )
        );

        // Then
        assertThat(results).isNotEmpty();
        results.forEach(result -> 
            assertThat(result.metadata().get("category"))
                .describedAs("All results should have matching category filter")
                .isEqualTo("retrieval")
        );
    }

    @Test
    @DisplayName("Retrieve memory - different agent isolation")
    void testRetrieveMemory_Positive_AgentIsolation() {
        // Given
        String otherAgentId = "other-agent-" + System.nanoTime();
        String query = "retrieval functionality";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                otherAgentId,
                sessionId,
                query,
                5,
                Map.of()
            )
        );

        // Then
        assertThat(results)
            .describedAs("Other agent should not see this agent's memories")
            .isEmpty();
    }

    @Test
    @DisplayName("Retrieve memory - special characters in query")
    void testRetrieveMemory_Positive_SpecialCharactersInQuery() {
        // Given
        String query = "memory systems @#$%";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of()
            )
        );

        // Then - Should not throw exception
        assertThat(results).isNotNull();
    }

    @Test
    @DisplayName("Retrieve memory - unicode query")
    void testRetrieveMemory_Positive_UnicodeQuery() {
        // Given
        String query = "memory 你好 мир";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of()
            )
        );

        // Then - Should not throw exception
        assertThat(results).isNotNull();
    }

    @Test
    @DisplayName("Retrieve memory - results are sorted by score")
    void testRetrieveMemory_Positive_ResultsSorted() {
        // Given
        String query = "memory";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of()
            )
        );

        // Then
        if (results.size() > 1) {
            for (int i = 0; i < results.size() - 1; i++) {
                assertThat(results.get(i).score()).isGreaterThanOrEqualTo(results.get(i + 1).score());
            }
        }
    }
}