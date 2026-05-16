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
 * Regression test for multimodal memory retrieval functionality.
 * Tests both positive and negative scenarios for multimodal memory retrieval.
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
        agentId = "test-agent";
        sessionId = "test-session";
        
        // Setup test data with multimodal content
        memoryFacade.write(
            agentId,
            sessionId,
            MemoryType.EPISODIC,
            "User shared a photo of a beautiful sunset at the beach with palm trees",
            Map.of("modality", "image", "location", "beach")
        );
        
        memoryFacade.write(
            agentId,
            sessionId,
            MemoryType.SEMANTIC,
            "Audio recording of birds chirping in a forest",
            Map.of("modality", "audio", "location", "forest")
        );
        
        memoryFacade.write(
            agentId,
            sessionId,
            MemoryType.EPISODIC,
            "Text note about planning a vacation to Hawaii",
            Map.of("modality", "text", "location", "Hawaii")
        );
    }

    @Test
    @DisplayName("Multimodal retrieval - positive scenario")
    void testMultimodalRetrieval_Positive() {
        // Given
        String query = "memories related to beach vacation";
        
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
        assertThat(results.get(0).content()).contains("beach");
        assertThat(results.get(0).metadata()).containsKey("modality");
        assertThat(results.get(0).metadata().get("modality")).isEqualTo("image");
    }

    @Test
    @DisplayName("Multimodal retrieval - filter by modality")
    void testMultimodalRetrieval_FilterByModality() {
        // Given
        String query = "natural sounds";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            agentId,
            sessionId,
            query,
            5,
            Map.of("modality", "audio")
        );

        // Then
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).content()).contains("birds chirping");
        assertThat(results.get(0).metadata().get("modality")).isEqualTo("audio");
    }

    @Test
    @DisplayName("Multimodal retrieval - negative scenario with no matching modality")
    void testMultimodalRetrieval_Negative_NoMatchingModality() {
        // Given
        String query = "space exploration";
        
        // When
        List<RetrievalResult> results = memoryFacade.retrieve(
            agentId,
            sessionId,
            query,
            5,
            Map.of("modality", "video")
        );

        // Then
        assertThat(results).isEmpty();
    }
}