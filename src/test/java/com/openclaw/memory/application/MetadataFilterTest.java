package com.openclaw.memory.application;

import com.openclaw.memory.domain.model.MemoryType;
import com.openclaw.memory.domain.model.RetrievalResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataFilterTest {

    // ── Empty filter ──────────────────────────────────────────────────────────

    @Test
    void emptyFilter_shouldPassAllResults() {
        List<RetrievalResult> results = List.of(
                result(Map.of("modality", "image")),
                result(Map.of("modality", "audio")));

        assertThat(RetrievalOrchestrator.applyMetadataFilter(results, Map.of()))
                .hasSize(2);
    }

    @Test
    void nullFilter_shouldPassAllResults() {
        List<RetrievalResult> results = List.of(result(Map.of("modality", "image")));
        assertThat(RetrievalOrchestrator.applyMetadataFilter(results, null))
                .hasSize(1);
    }

    // ── Single-key filter ─────────────────────────────────────────────────────

    @Test
    void singleFilter_shouldKeepMatchingResults() {
        List<RetrievalResult> results = List.of(
                result(Map.of("modality", "image", "location", "beach")),
                result(Map.of("modality", "audio", "location", "forest")),
                result(Map.of("modality", "image", "location", "city")));

        List<RetrievalResult> filtered =
                RetrievalOrchestrator.applyMetadataFilter(results, Map.of("modality", "image"));

        assertThat(filtered).hasSize(2);
        filtered.forEach(r ->
                assertThat(r.metadata().get("modality")).isEqualTo("image"));
    }

    @Test
    void singleFilter_noMatch_shouldReturnEmpty() {
        List<RetrievalResult> results = List.of(
                result(Map.of("modality", "image")),
                result(Map.of("modality", "video")));

        assertThat(RetrievalOrchestrator.applyMetadataFilter(
                results, Map.of("modality", "animation"))).isEmpty();
    }

    // ── Multi-key filter ──────────────────────────────────────────────────────

    @Test
    void multiFilter_shouldRequireAllKeysToMatch() {
        List<RetrievalResult> results = List.of(
                result(Map.of("modality", "image", "location", "beach")),  // matches both
                result(Map.of("modality", "image", "location", "city")),   // wrong location
                result(Map.of("modality", "audio", "location", "beach"))); // wrong modality

        List<RetrievalResult> filtered = RetrievalOrchestrator.applyMetadataFilter(
                results, Map.of("modality", "image", "location", "beach"));

        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).metadata())
                .containsEntry("modality", "image")
                .containsEntry("location", "beach");
    }

    // ── Missing metadata key ──────────────────────────────────────────────────

    @Test
    void filter_shouldExcludeResults_withMissingKey() {
        List<RetrievalResult> results = List.of(
                result(Map.of("modality", "image")),     // has key
                result(Map.of("location", "beach")));    // missing "modality" key

        List<RetrievalResult> filtered =
                RetrievalOrchestrator.applyMetadataFilter(results, Map.of("modality", "image"));

        assertThat(filtered).hasSize(1);
    }

    // ── Type coercion (toString comparison) ───────────────────────────────────

    @Test
    void filter_shouldCoerceToString_forComparison() {
        // metadata values stored as Integer, filter uses String
        List<RetrievalResult> results = List.of(
                result(Map.of("year", 2024)));

        assertThat(RetrievalOrchestrator.applyMetadataFilter(
                results, Map.of("year", "2024"))).hasSize(1);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static RetrievalResult result(Map<String, Object> metadata) {
        return new RetrievalResult(
                UUID.randomUUID(), MemoryType.EPISODIC,
                "content", 0.8, metadata, null);
    }
}
