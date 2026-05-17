package com.openclaw.memory.application;

import com.openclaw.memory.domain.model.MemoryType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Business metrics for memory operations, exposed via Micrometer → Prometheus.
 *
 * Metrics produced:
 *   memory_writes_total{type}           — write count by MemoryType
 *   memory_write_duration_seconds       — write latency histogram
 *   memory_retrievals_total             — retrieval call count
 *   memory_retrieval_duration_seconds   — retrieval latency histogram
 *   memory_retrieval_results            — distribution of result-set sizes
 *   memory_conflicts_detected_total     — conflict events detected at write time
 */
@Component
public class MemoryMetrics {

    private final MeterRegistry registry;

    private final Timer      writeTimer;
    private final Timer      retrievalTimer;
    private final DistributionSummary retrievalResults;
    private final Counter    conflictCounter;

    public MemoryMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.writeTimer = Timer.builder("memory.write.duration")
                .description("Time spent persisting a memory record")
                .register(registry);

        this.retrievalTimer = Timer.builder("memory.retrieval.duration")
                .description("Time spent retrieving memories")
                .register(registry);

        this.retrievalResults = DistributionSummary.builder("memory.retrieval.results")
                .description("Number of results returned per retrieval call")
                .register(registry);

        this.conflictCounter = Counter.builder("memory.conflicts.detected.total")
                .description("Number of subject-conflicts detected during write")
                .register(registry);
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    public Timer.Sample startWrite() {
        return Timer.start(registry);
    }

    public void stopWrite(Timer.Sample sample, MemoryType type) {
        sample.stop(writeTimer);
        Counter.builder("memory.writes.total")
                .description("Total memory write operations")
                .tag("type", type != null ? type.name() : "UNKNOWN")
                .register(registry)
                .increment();
    }

    // ── Retrieval ─────────────────────────────────────────────────────────────

    public Timer.Sample startRetrieval() {
        return Timer.start(registry);
    }

    public void stopRetrieval(Timer.Sample sample, int resultCount) {
        sample.stop(retrievalTimer);
        Counter.builder("memory.retrievals.total")
                .description("Total memory retrieval calls")
                .register(registry)
                .increment();
        retrievalResults.record(resultCount);
    }

    // ── Conflicts ─────────────────────────────────────────────────────────────

    public void recordConflict() {
        conflictCounter.increment();
    }
}
