package com.openclaw.memory.application;

import com.openclaw.memory.domain.model.MemoryType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryMetricsTest {

    private SimpleMeterRegistry registry;
    private MemoryMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics  = new MemoryMetrics(registry);
    }

    // ── write ─────────────────────────────────────────────────────────────────

    @Test
    void write_shouldIncrementCounter_withTypeTag() {
        Timer.Sample s = metrics.startWrite();
        metrics.stopWrite(s, MemoryType.EPISODIC);

        Counter c = registry.find("memory.writes.total")
                .tag("type", "EPISODIC").counter();
        assertThat(c).isNotNull();
        assertThat(c.count()).isEqualTo(1.0);
    }

    @Test
    void write_shouldRecordTimer() {
        Timer.Sample s = metrics.startWrite();
        metrics.stopWrite(s, MemoryType.WORKING);

        assertThat(registry.find("memory.write.duration").timer())
                .isNotNull()
                .extracting(Timer::count).isEqualTo(1L);
    }

    @Test
    void write_differentTypes_shouldHaveSeparateCounters() {
        metrics.stopWrite(metrics.startWrite(), MemoryType.EPISODIC);
        metrics.stopWrite(metrics.startWrite(), MemoryType.EPISODIC);
        metrics.stopWrite(metrics.startWrite(), MemoryType.SEMANTIC_WIKI);

        assertThat(registry.find("memory.writes.total")
                .tag("type", "EPISODIC").counter().count()).isEqualTo(2.0);
        assertThat(registry.find("memory.writes.total")
                .tag("type", "SEMANTIC_WIKI").counter().count()).isEqualTo(1.0);
    }

    // ── retrieval ─────────────────────────────────────────────────────────────

    @Test
    void retrieval_shouldIncrementCounter() {
        metrics.stopRetrieval(metrics.startRetrieval(), 3);

        assertThat(registry.find("memory.retrievals.total").counter())
                .isNotNull()
                .extracting(Counter::count).isEqualTo(1.0);
    }

    @Test
    void retrieval_shouldRecordResultCount() {
        metrics.stopRetrieval(metrics.startRetrieval(), 5);
        metrics.stopRetrieval(metrics.startRetrieval(), 2);

        var summary = registry.find("memory.retrieval.results").summary();
        assertThat(summary).isNotNull();
        assertThat(summary.count()).isEqualTo(2);
        assertThat(summary.totalAmount()).isEqualTo(7.0);
    }

    @Test
    void retrieval_shouldRecordTimer() {
        metrics.stopRetrieval(metrics.startRetrieval(), 0);

        assertThat(registry.find("memory.retrieval.duration").timer())
                .isNotNull()
                .extracting(Timer::count).isEqualTo(1L);
    }

    // ── conflicts ─────────────────────────────────────────────────────────────

    @Test
    void conflict_shouldIncrementCounter() {
        metrics.recordConflict();
        metrics.recordConflict();

        assertThat(registry.find("memory.conflicts.detected.total").counter())
                .isNotNull()
                .extracting(Counter::count).isEqualTo(2.0);
    }
}
