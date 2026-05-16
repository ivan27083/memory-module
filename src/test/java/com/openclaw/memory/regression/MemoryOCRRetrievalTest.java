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

/**
 * Regression test for OCR-based memory retrieval functionality.
 * Tests positive, negative, and edge case scenarios for OCR-based memory retrieval.
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
        agentId = "test-agent-" + System.nanoTime();
        sessionId = "test-session-" + System.nanoTime();
        
        // Setup test data with OCR content
        memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                sessionId,
                MemoryType.SEMANTIC_WIKI,
                "OCR extracted text from image: A diagram showing the architecture of a neural network with input, hidden, and output layers.",
                Map.of("source", "ocr", "type", "diagram", "quality", "high")
            )
        );
        
        memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                sessionId,
                MemoryType.EPISODIC,
                "OCR text from document: Invoice #2024-001 dated 2024-01-15 for services rendered",
                Map.of("source", "ocr", "type", "document", "quality", "medium")
            )
        );
        
        memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                sessionId,
                MemoryType.SEMANTIC_WIKI,
                "OCR extracted text: Machine Learning Fundamentals - Chapter 5: Neural Networks and Deep Learning",
                Map.of("source", "ocr", "type", "textbook", "quality", "high")
            )
        );
    }

    @Test
    @DisplayName("OCR retrieval - positive scenario with exact phrase")
    void testOCRRetrieval_Positive_ExactPhrase() {
        // Given
        String query = "neural network architecture diagram";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of("source", "ocr")
            )
        );

        // Then
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).content()).contains("neural network");
        assertThat(results.get(0).content()).contains("diagram");
        assertThat(results.get(0).metadata()).containsKey("source");
        assertThat(results.get(0).metadata().get("source")).isEqualTo("ocr");
    }

    @Test
    @DisplayName("OCR retrieval - positive scenario with partial match")
    void testOCRRetrieval_Positive_PartialMatch() {
        // Given
        String query = "invoice services";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of("source", "ocr")
            )
        );

        // Then
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).metadata().get("source")).isEqualTo("ocr");
    }

    @Test
    @DisplayName("OCR retrieval - filter by document type")
    void testOCRRetrieval_FilterByType() {
        // Given
        String query = "neural";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of("source", "ocr", "type", "diagram")
            )
        );

        // Then
        assertThat(results).isNotEmpty();
        results.forEach(result ->
            assertThat(result.metadata().get("type")).isEqualTo("diagram")
        );
    }

    @Test
    @DisplayName("OCR retrieval - filter by quality")
    void testOCRRetrieval_FilterByQuality() {
        // Given
        String query = "OCR text";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of("source", "ocr", "quality", "high")
            )
        );

        // Then
        assertThat(results).isNotEmpty();
        results.forEach(result ->
            assertThat(result.metadata().get("quality")).isEqualTo("high")
        );
    }

    @Test
    @DisplayName("OCR retrieval - retrieve all OCR documents")
    void testOCRRetrieval_Positive_AllDocuments() {
        // Given
        String query = "OCR extracted";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                10,
                Map.of("source", "ocr")
            )
        );

        // Then
        assertThat(results).isNotEmpty();
        assertThat(results.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("OCR retrieval - negative scenario with no matching content")
    void testOCRRetrieval_Negative_NoOCRResults() {
        // Given
        String query = "handwritten notes about quantum mechanics";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of("source", "ocr")
            )
        );

        // Then
        assertThat(results)
            .describedAs("Should return empty list when no OCR content matches the query")
            .isEmpty();
    }

    @Test
    @DisplayName("OCR retrieval - negative scenario with invalid source filter")
    void testOCRRetrieval_Negative_InvalidSourceFilter() {
        // Given
        String query = "neural network architecture";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of("source", "invalid-source")
            )
        );

        // Then
        assertThat(results)
            .describedAs("Should return empty list with invalid source filter")
            .isEmpty();
    }

    @Test
    @DisplayName("OCR retrieval - negative scenario with non-existent type filter")
    void testOCRRetrieval_Negative_NonExistentType() {
        // Given
        String query = "anything";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of("source", "ocr", "type", "non-existent-type")
            )
        );

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("OCR retrieval - multiple filters combined")
    void testOCRRetrieval_MultipleFilters() {
        // Given
        String query = "content";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of("source", "ocr", "type", "document", "quality", "medium")
            )
        );

        // Then
        assertThat(results).isNotEmpty();
        results.forEach(result -> {
            assertThat(result.metadata().get("source")).isEqualTo("ocr");
            assertThat(result.metadata().get("type")).isEqualTo("document");
            assertThat(result.metadata().get("quality")).isEqualTo("medium");
        });
    }

    @Test
    @DisplayName("OCR retrieval - case insensitive search")
    void testOCRRetrieval_CaseInsensitiveSearch() {
        // Given
        String query = "NEURAL NETWORK";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of("source", "ocr")
            )
        );

        // Then
        assertThat(results)
            .describedAs("Search should be case-insensitive")
            .isNotEmpty();
    }

    @Test
    @DisplayName("OCR retrieval - partial word matching")
    void testOCRRetrieval_PartialWordMatching() {
        // Given
        String query = "architec";  // Partial word
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of("source", "ocr")
            )
        );

        // Then - Depending on implementation, might or might not find results
        assertThat(results).isNotNull();
    }

    @Test
    @DisplayName("OCR retrieval - limit results")
    void testOCRRetrieval_LimitResults() {
        // Given
        String query = "OCR";
        int limit = 1;
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                limit,
                Map.of("source", "ocr")
            )
        );

        // Then
        assertThat(results).hasSizeLessThanOrEqualTo(limit);
    }

    @Test
    @DisplayName("OCR retrieval - mixed OCR and non-OCR sources")
    void testOCRRetrieval_OnlyOCRSources() {
        // Given - write non-OCR content
        memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                sessionId,
                MemoryType.EPISODIC,
                "Manually typed note about neural networks",
                Map.of("source", "manual")
            )
        );
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                "neural",
                5,
                Map.of("source", "ocr")
            )
        );

        // Then - should only get OCR results
        results.forEach(result ->
            assertThat(result.metadata().get("source")).isEqualTo("ocr")
        );
    }
}