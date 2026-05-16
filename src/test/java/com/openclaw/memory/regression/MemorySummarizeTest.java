package com.openclaw.memory.regression;

import com.openclaw.memory.MemoryModuleApplication;
import com.openclaw.memory.application.MemoryFacade;
import com.openclaw.memory.domain.model.MemoryRecord;
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
 * Regression test for memory summarization functionality.
 * Tests both positive and negative scenarios for memory summarization.
 */
@SpringBootTest(classes = MemoryModuleApplication.class)
@DisplayName("Memory Summarize Regression Tests")
public class MemorySummarizeTest {

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
            "This is a long document about AI and machine learning. It contains information about neural networks, deep learning, and natural language processing. The document also discusses the history of AI and its future prospects.",
            Map.of("type", "document")
        );
    }

    @Test
    @DisplayName("Summarize memory - positive scenario")
    void testSummarizeMemory_Positive() {
        // Given
        String query = "Summarize the document about AI";
        
        // When
        List<MemoryRecord> summaries = memoryFacade.summarize(
            agentId,
            sessionId,
            query,
            3,
            Map.of()
        );

        // Then
        assertThat(summaries).isNotEmpty();
        assertThat(summaries.get(0).content()).isNotEmpty();
        assertThat(summaries.get(0).content()).doesNotContain("This is a long document");
        assertThat(summaries.get(0).content()).containsAnyOf("AI", "machine learning", "neural networks");
    }

    @Test
    @DisplayName("Summarize memory - negative scenario with no relevant memories")
    void testSummarizeMemory_Negative_NoRelevantMemories() {
        // Given
        String query = "Summarize the document about quantum physics";
        
        // When
        List<MemoryRecord> summaries = memoryFacade.summarize(
            agentId,
            sessionId,
            query,
            3,
            Map.of()
        );

        // Then
        assertThat(summaries).isEmpty();
    }

    @Test
    @DisplayName("Summarize memory - negative scenario with null query")
    void testSummarizeMemory_Negative_NullQuery() {
        // Given
        String query = null;
        
        // When & Then
        assertThatThrownBy(() -> memoryFacade.summarize(
            agentId,
            sessionId,
            query,
            3,
            Map.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }
}