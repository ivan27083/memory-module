package com.openclaw.memory.working_memory;

import com.openclaw.memory.retrieval.RetrievalResult;
import com.openclaw.memory.retrieval.RetrieverExplanation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SalienceScorerTest {

    private final SalienceScorer scorer = new SalienceScorer();

    // ── Composite score ordering ──────────────────────────────────────────────

    @Test
    void higherRelevance_shouldProduceHigherSalience() {
        WorkingMemoryComposer.SelectedMemory high = memory(0.9, 0.5, null);
        WorkingMemoryComposer.SelectedMemory low  = memory(0.1, 0.5, null);

        assertThat(scorer.score(high, Instant.now()))
                .isGreaterThan(scorer.score(low, Instant.now()));
    }

    @Test
    void recentMemory_shouldScoreHigherThanOldMemory() {
        Instant recent = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant old    = Instant.now().minus(720, ChronoUnit.HOURS); // 30 days

        WorkingMemoryComposer.SelectedMemory fresh = memory(0.5, 0.5, recent);
        WorkingMemoryComposer.SelectedMemory stale = memory(0.5, 0.5, old);

        assertThat(scorer.score(fresh, Instant.now()))
                .isGreaterThan(scorer.score(stale, Instant.now()));
    }

    @Test
    void higherConfidence_shouldProduceHigherSalience() {
        WorkingMemoryComposer.SelectedMemory highConf = memoryWithConf(0.5, 0.9, null);
        WorkingMemoryComposer.SelectedMemory lowConf  = memoryWithConf(0.5, 0.1, null);

        assertThat(scorer.score(highConf, Instant.now()))
                .isGreaterThan(scorer.score(lowConf, Instant.now()));
    }

    @Test
    void salience_shouldBeBetweenZeroAndOne() {
        WorkingMemoryComposer.SelectedMemory m = memory(1.0, 0.5, Instant.now());
        double s = scorer.score(m, Instant.now());
        assertThat(s).isBetween(0.0, 1.0);
    }

    @Test
    void noMetadata_shouldReturnDefaultSalience() {
        WorkingMemoryComposer.SelectedMemory m = new WorkingMemoryComposer.SelectedMemory(
                null, 0.5, WorkingMemoryComposer.SelectionReason.RELEVANCE_MATCH, "id-1");
        // No retrievalExplanation — should not throw
        double s = scorer.score(m, Instant.now());
        assertThat(s).isGreaterThan(0.0);
    }

    // ── validFrom extraction ──────────────────────────────────────────────────

    @Test
    void validFromOf_shouldParseInstantFromMetadata() {
        Instant expected = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        WorkingMemoryComposer.SelectedMemory m = memory(0.5, 0.5, expected);
        Instant actual = SalienceScorer.validFromOf(m);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void validFromOf_shouldReturnNull_whenAbsent() {
        WorkingMemoryComposer.SelectedMemory m = memory(0.5, 0.5, null);
        assertThat(SalienceScorer.validFromOf(m)).isNull();
    }

    // ── confidence extraction ─────────────────────────────────────────────────

    @Test
    void confidenceOf_shouldDefault_whenAbsent() {
        WorkingMemoryComposer.SelectedMemory m = memory(0.5, 0.5, null); // metadata has validFrom only
        // confidence not in meta → default 0.5
        assertThat(SalienceScorer.confidenceOf(m)).isEqualTo(0.5);
    }

    @Test
    void confidenceOf_shouldReadFromMetadata() {
        WorkingMemoryComposer.SelectedMemory m = memoryWithConf(0.5, 0.85, null);
        assertThat(SalienceScorer.confidenceOf(m)).isEqualTo(0.85);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static WorkingMemoryComposer.SelectedMemory memory(double relevance,
                                                                double confidence,
                                                                Instant validFrom) {
        return memoryWithConf(relevance, confidence, validFrom);
    }

    private static WorkingMemoryComposer.SelectedMemory memoryWithConf(double relevance,
                                                                        double confidence,
                                                                        Instant validFrom) {
        Map<String, Object> meta = buildMeta(confidence, validFrom);
        var explanation = new RetrievalResult(
                "id-" + relevance, "content", relevance, meta,
                new RetrieverExplanation("test", List.of(), 0, 0, 0, "test"),
                null);

        WorkingMemoryComposer.SelectedMemory m = new WorkingMemoryComposer.SelectedMemory(
                null, relevance, WorkingMemoryComposer.SelectionReason.RELEVANCE_MATCH,
                "id-" + relevance);
        m.retrievalExplanation = explanation;
        return m;
    }

    private static Map<String, Object> buildMeta(double confidence, Instant validFrom) {
        var meta = new java.util.LinkedHashMap<String, Object>();
        meta.put("confidence", confidence);
        if (validFrom != null) meta.put("validFrom", validFrom);
        return meta;
    }
}
