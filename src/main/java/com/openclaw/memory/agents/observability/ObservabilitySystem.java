package com.openclaw.memory.agents.observability;

import io.micrometer.core.instrument.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Observability System - Metrics collection and tracing.
 * 
 * Tracks:
 * - Retrieval performance
 * - Indexing throughput
 * - Cache hit rates
 * - Latency percentiles
 * - Agent execution metrics
 */
@Slf4j
public class ObservabilitySystem {
    
    private final MeterRegistry meterRegistry;
    
    // Core metrics
    private final AtomicLong totalRetrievals = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    
    private final Timer retrievalTimer;
    private final Timer indexingTimer;
    private final Timer compositionTimer;
    private final Timer conflictResolutionTimer;
    
    private final AtomicInteger activeMemories = new AtomicInteger(0);
    private final AtomicInteger tier1Size = new AtomicInteger(0);
    private final AtomicInteger tier2Size = new AtomicInteger(0);
    private final AtomicInteger tier3Size = new AtomicInteger(0);
    
    private final Map<String, AgentMetrics> agentMetrics = new ConcurrentHashMap<>();
    private final Map<String, LatencyTracker> latencyTrackers = new ConcurrentHashMap<>();
    
    public ObservabilitySystem(MeterRegistry registry) {
        this.meterRegistry = registry;
        
        // Timer setup
        this.retrievalTimer = Timer.builder("memory.retrieval.time")
            .description("Retrieval latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
        
        this.indexingTimer = Timer.builder("memory.indexing.time")
            .description("Indexing latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
        
        this.compositionTimer = Timer.builder("memory.composition.time")
            .description("Working memory composition latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
        
        this.conflictResolutionTimer = Timer.builder("memory.conflict.time")
            .description("Conflict resolution latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
        
        // Gauges
        Gauge.builder("memory.cache.hits", cacheHits::get)
            .description("Total cache hits")
            .register(registry);
        
        Gauge.builder("memory.cache.misses", cacheMisses::get)
            .description("Total cache misses")
            .register(registry);
        
        Gauge.builder("memory.active.count", activeMemories::get)
            .description("Total active memories")
            .register(registry);
        
        Gauge.builder("memory.tier1.size", tier1Size::get)
            .description("Tier 1 (working memory) size")
            .register(registry);
        
        Gauge.builder("memory.tier2.size", tier2Size::get)
            .description("Tier 2 (compressed) size")
            .register(registry);
        
        Gauge.builder("memory.tier3.size", tier3Size::get)
            .description("Tier 3 (archived) size")
            .register(registry);
    }
    
    /**
     * Record retrieval operation
     */
    public void recordRetrieval(long durationMs, boolean cacheHit) {
        totalRetrievals.incrementAndGet();
        retrievalTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        if (cacheHit) {
            cacheHits.incrementAndGet();
        } else {
            cacheMisses.incrementAndGet();
        }
    }
    
    /**
     * Record indexing operation
     */
    public void recordIndexing(long durationMs, int itemsIndexed) {
        indexingTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        Counter.builder("memory.indexing.items")
            .description("Total items indexed")
            .register(meterRegistry)
            .increment(itemsIndexed);
    }
    
    /**
     * Record working memory composition
     */
    public void recordComposition(long durationMs, int memoriesSelected) {
        compositionTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        Counter.builder("memory.composition.selections")
            .description("Total memories selected")
            .register(meterRegistry)
            .increment(memoriesSelected);
    }
    
    /**
     * Record conflict resolution
     */
    public void recordConflictResolution(long durationMs, int conflictsResolved) {
        conflictResolutionTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        Counter.builder("memory.conflicts.resolved")
            .description("Total conflicts resolved")
            .register(meterRegistry)
            .increment(conflictsResolved);
    }
    
    /**
     * Record agent execution
     */
    public void recordAgentExecution(String agentName, long durationMs, boolean success) {
        AgentMetrics metrics = agentMetrics.computeIfAbsent(agentName, k -> 
            new AgentMetrics(agentName, meterRegistry));
        
        metrics.recordExecution(durationMs, success);
    }
    
    /**
     * Update memory tier statistics
     */
    public void updateMemoryStats(int tier1, int tier2, int tier3) {
        tier1Size.set(tier1);
        tier2Size.set(tier2);
        tier3Size.set(tier3);
        activeMemories.set(tier1 + tier2 + tier3);
    }
    
    /**
     * Get cache hit ratio
     */
    public double getCacheHitRatio() {
        long total = cacheHits.get() + cacheMisses.get();
        if (total == 0) return 0;
        return (double) cacheHits.get() / total;
    }
    
    /**
     * Get retrieval latency statistics
     */
    public LatencyStats getRetrievalLatencyStats() {
        return new LatencyStats(
            "retrieval",
            retrievalTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS),
            retrievalTimer.max(java.util.concurrent.TimeUnit.MILLISECONDS),
            retrievalTimer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)
        );
    }
    
    /**
     * Get system health report
     */
    public SystemHealthReport getHealthReport() {
        SystemHealthReport report = new SystemHealthReport();
        report.cacheHitRatio = getCacheHitRatio();
        report.totalRetrievals = totalRetrievals.get();
        report.retrievalLatencyMs = retrievalTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS);
        report.indexingLatencyMs = indexingTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS);
        report.tier1Size = tier1Size.get();
        report.tier2Size = tier2Size.get();
        report.tier3Size = tier3Size.get();
        
        for (AgentMetrics metrics : agentMetrics.values()) {
            report.agentStats.put(metrics.name, metrics.getStats());
        }
        
        return report;
    }
    
    // ===== Data Models =====
    
    @Data
    public static class AgentMetrics {
        public String name;
        private final AtomicLong executions = new AtomicLong(0);
        private final AtomicLong successes = new AtomicLong(0);
        private final AtomicLong failures = new AtomicLong(0);
        private final Timer executionTimer;
        
        public AgentMetrics(String name, MeterRegistry registry) {
            this.name = name;
            this.executionTimer = Timer.builder("agent.execution")
                .tag("agent", name)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        }
        
        public void recordExecution(long durationMs, boolean success) {
            executions.incrementAndGet();
            if (success) {
                successes.incrementAndGet();
            } else {
                failures.incrementAndGet();
            }
            executionTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        
        public AgentStats getStats() {
            return new AgentStats(name, executions.get(), successes.get(), failures.get());
        }
    }
    
    @Data
    public static class AgentStats {
        public String agent;
        public long totalExecutions;
        public long successfulExecutions;
        public long failedExecutions;
        
        public AgentStats(String agent, long total, long success, long failed) {
            this.agent = agent;
            this.totalExecutions = total;
            this.successfulExecutions = success;
            this.failedExecutions = failed;
        }
        
        public double getSuccessRate() {
            return totalExecutions == 0 ? 0 : (double) successfulExecutions / totalExecutions;
        }
    }
    
    @Data
    public static class LatencyStats {
        public String operation;
        public double meanMs;
        public double maxMs;
        public double totalMs;
    }
    
    @Data
    public static class LatencyTracker {
        private final List<Long> samples = new ArrayList<>();
        private final int maxSamples = 1000;
        
        public void record(long durationMs) {
            synchronized (samples) {
                samples.add(durationMs);
                if (samples.size() > maxSamples) {
                    samples.remove(0);
                }
            }
        }
        
        public double getPercentile(double p) {
            synchronized (samples) {
                if (samples.isEmpty()) return 0;
                List<Long> sorted = new ArrayList<>(samples);
                Collections.sort(sorted);
                int index = (int) ((p / 100.0) * sorted.size());
                return sorted.get(Math.min(index, sorted.size() - 1));
            }
        }
    }
    
    @Data
    public static class SystemHealthReport {
        public double cacheHitRatio;
        public long totalRetrievals;
        public double retrievalLatencyMs;
        public double indexingLatencyMs;
        public int tier1Size;
        public int tier2Size;
        public int tier3Size;
        public Map<String, AgentStats> agentStats = new HashMap<>();
        
        public boolean isHealthy() {
            return cacheHitRatio > 0.3 && retrievalLatencyMs < 300;
        }
    }
}
