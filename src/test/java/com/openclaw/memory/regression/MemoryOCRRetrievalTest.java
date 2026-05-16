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
 * Regression test for OCR-based memory retrieval functionality.
 * Tests both positive and negative scenarios for OCR-based memory retrieval.
 */
@SpringBootTest(classes = MemoryModuleApplication.class)
@DisplayName("OCR Retrieval Regression Tests")
public class MemoryOCRRetrievalTest {

    @Autowired
    private MemoryFacade memoryFacade;

    private String agentId;
    private String sessionId;

    @BeforeEach
    void setUp() {
        agentId = "test-agent";
        sessionId = "test-session";
        
        // Setup test data with OCR content
        memoryFacade.write(
            agentId,
            sessionId,
            MemoryType.SEMANTIC,
            "OCR extracted text from image: A diagram showing the architecture of a neural network with input, hidden, and output layers.",
            Map.of("source", "ocr", "type", "diagram")
        );
    }

    @Test
    @DisplayName("OCR retrieval - positive scenario")
    void testOCRRetrieval_Positive() {
        // Given
        String query = "neural network architecture diagram";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            agentId,
            sessionId,
            query,
            5,
            Map.of("source", "ocr")
        );

        // Then
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).content()).contains("neural network");
        assertThat(results.get(0).content()).contains("diagram");
        assertThat(results.get(0).metadata()).containsKey("source");
        assertThat(results.get(0).metadata().get("source")).isEqualTo("ocr");
    }

    @Test
    @DisplayName("OCR retrieval - negative scenario with no OCR results")
    void testOCRRetrieval_Negative_NoOCRResults() {
        // Given
        String query = "handwritten notes about quantum mechanics";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            agentId,
            sessionId,
            query,
            5,
            Map.of("source", "ocr")
        );

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("OCR retrieval - negative scenario with invalid source filter")
    void testOCRRetrieval_Negative_InvalidSourceFilter() {
        // Given
        String query = "neural network architecture";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            agentId,
            sessionId,
            query,
            5,
            Map.of("source", "invalid-source")
        );

        // Then
        assertThat(results).isEmpty();
    }
}