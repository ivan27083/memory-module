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
 * Regression test for multimodal memory retrieval functionality.
 * Tests positive, negative, and edge case scenarios for multimodal memory retrieval.
 */
@SpringBootTest(classes = MemoryModuleApplication.class)
@DisplayName("Multimodal Retrieval Regression Tests")
public class MemoryMultimodalRetrievalTest {

    @Autowired
    private MemoryFacade memoryFacade;

    private String agentId;
    private String sessionId;

    @BeforeEach
    void setUp() {
        agentId = "test-agent-" + System.nanoTime();
        sessionId = "test-session-" + System.nanoTime();
        
        // Setup test data with multimodal content
        memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                sessionId,
                MemoryType.EPISODIC,
                "User shared a photo of a beautiful sunset at the beach with palm trees",
                Map.of("modality", "image", "location", "beach", "subject", "sunset")
            )
        );
        
        memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                sessionId,
                MemoryType.SEMANTIC_WIKI,
                "Audio recording of birds chirping in a forest environment",
                Map.of("modality", "audio", "location", "forest", "subject", "wildlife")
            )
        );
        
        memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                sessionId,
                MemoryType.EPISODIC,
                "Text note about planning a vacation to Hawaii with family",
                Map.of("modality", "text", "location", "Hawaii", "subject", "vacation")
            )
        );
        
        memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                sessionId,
                MemoryType.EPISODIC,
                "Video tutorial on how to properly edit photos using Adobe Lightroom",
                Map.of("modality", "video", "location", "studio", "subject", "editing")
            )
        );
    }

    @Test
    @DisplayName("Multimodal retrieval - positive scenario")
    void testMultimodalRetrieval_Positive() {
        // Given
        String query = "beach vacation sunset";
        
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
            .describedAs("Should find multimodal memories matching the query")
            .isNotEmpty();
        assertThat(results.get(0).metadata()).containsKey("modality");
    }

    @Test
    @DisplayName("Multimodal retrieval - filter by image modality")
    void testMultimodalRetrieval_FilterByImageModality() {
        // Given
        String query = "beach sunset";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of("modality", "image")
            )
        );

        // Then
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).content()).contains("sunset");
        assertThat(results.get(0).metadata().get("modality")).isEqualTo("image");
    }

    @Test
    @DisplayName("Multimodal retrieval - filter by audio modality")
    void testMultimodalRetrieval_FilterByAudioModality() {
        // Given
        String query = "birds chirping";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of("modality", "audio")
            )
        );

        // Then
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).content()).contains("birds");
        assertThat(results.get(0).metadata().get("modality")).isEqualTo("audio");
    }

    @Test
    @DisplayName("Multimodal retrieval - filter by text modality")
    void testMultimodalRetrieval_FilterByTextModality() {
        // Given
        String query = "Hawaii vacation";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of("modality", "text")
            )
        );

        // Then
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).content()).contains("Hawaii");
        assertThat(results.get(0).metadata().get("modality")).isEqualTo("text");
    }

    @Test
    @DisplayName("Multimodal retrieval - filter by video modality")
    void testMultimodalRetrieval_FilterByVideoModality() {
        // Given
        String query = "photo editing tutorial";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of("modality", "video")
            )
        );

        // Then
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).metadata().get("modality")).isEqualTo("video");
    }

    @Test
    @DisplayName("Multimodal retrieval - filter by location")
    void testMultimodalRetrieval_FilterByLocation() {
        // Given
        String query = "location memories";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of("location", "beach")
            )
        );

        // Then
        assertThat(results).isNotEmpty();
        results.forEach(result ->
            assertThat(result.metadata().get("location")).isEqualTo("beach")
        );
    }

    @Test
    @DisplayName("Multimodal retrieval - negative scenario with no matching modality")
    void testMultimodalRetrieval_Negative_NoMatchingModality() {
        // Given
        String query = "any query";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of("modality", "animation")
            )
        );

        // Then
        assertThat(results)
            .describedAs("Expected empty list when retrieving with non-existent modality 'animation'")
            .isEmpty();
    }

    @Test
    @DisplayName("Multimodal retrieval - negative scenario with non-matching location filter")
    void testMultimodalRetrieval_Negative_NoMatchingLocation() {
        // Given
        String query = "sunset beach";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of("location", "mountains")
            )
        );

        // Then
        assertThat(results)
            .describedAs("Expected empty list when location filter doesn't match any memories")
            .isEmpty();
    }

    @Test
    @DisplayName("Multimodal retrieval - multiple filters combined")
    void testMultimodalRetrieval_MultipleFilters() {
        // Given
        String query = "content";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of("modality", "image", "location", "beach")
            )
        );

        // Then
        assertThat(results).isNotEmpty();
        results.forEach(result -> {
            assertThat(result.metadata().get("modality")).isEqualTo("image");
            assertThat(result.metadata().get("location")).isEqualTo("beach");
        });
    }

    @Test
    @DisplayName("Multimodal retrieval - filter by subject")
    void testMultimodalRetrieval_FilterBySubject() {
        // Given
        String query = "content";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                query,
                5,
                Map.of("subject", "vacation")
            )
        );

        // Then
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).metadata().get("subject")).isEqualTo("vacation");
    }

    @Test
    @DisplayName("Multimodal retrieval - all modalities retrievable")
    void testMultimodalRetrieval_AllModalities() {
        // When - retrieve without filters
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId,
                sessionId,
                "memory content",
                10,
                Map.of()
            )
        );

        // Then - should get results from all modalities
        assertThat(results).isNotEmpty();
        assertThat(results.stream()
            .map(r -> r.metadata().get("modality"))
            .distinct()
            .count()).isGreaterThan(1);
    }
}