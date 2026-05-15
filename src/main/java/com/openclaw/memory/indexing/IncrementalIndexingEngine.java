package com.openclaw.memory.indexing;

import com.openclaw.memory.blackboard.Artifact;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/**
 * Incremental Indexing System - DAG-based cache pipeline.
 * 
 * Inspired by CocoIndex. Supports:
 * - Cacheable pipeline nodes
 * - Hash-based invalidation
 * - Partial recomputation
 * - Incremental updates
 * 
 * Pipeline stages:
 * raw_event → normalize → chunk → embed → index → graph_update
 */
@Slf4j
public class IncrementalIndexingEngine {
    
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, PipelineNode> nodes = new HashMap<>();
    private final ExecutorService executor;
    private final MessageDigest hasher;
    
    public IncrementalIndexingEngine(int parallelism) throws Exception {
        this.executor = Executors.newFixedThreadPool(parallelism);
        this.hasher = MessageDigest.getInstance("SHA-256");
    }
    
    /**
     * Register a pipeline stage
     */
    public void registerNode(String nodeName, PipelineNode node) {
        nodes.put(nodeName, node);
        log.info("Registered pipeline node: {}", nodeName);
    }
    
    /**
     * Execute indexing pipeline with caching
     */
    public IndexingResult executeIndexing(Artifact artifact) throws Exception {
        IndexingResult result = new IndexingResult(artifact.getArtifactId());
        
        // Compute input hash
        String inputHash = computeHash(artifact);
        
        // Check cache
        CacheEntry cached = cache.get(artifact.getArtifactId());
        if (cached != null && cached.inputHash.equals(inputHash)) {
            log.debug("Cache hit for artifact: {}", artifact.getArtifactId());
            result.cached = true;
            result.outputs = cached.outputs;
            result.cacheTimeMs = cached.computedAt;
            return result;
        }
        
        // Execute pipeline DAG
        Map<String, StageOutput> stageOutputs = new LinkedHashMap<>();
        long startTime = System.currentTimeMillis();
        
        // Stage 1: Normalize
        StageOutput normalized = executeStage("normalize", artifact, null);
        stageOutputs.put("normalize", normalized);
        
        // Stage 2: Chunk (depends on normalize)
        StageOutput chunked = executeStage("chunk", artifact, normalized);
        stageOutputs.put("chunk", chunked);
        
        // Stage 3: Embed (depends on chunk)
        StageOutput embedded = executeStage("embed", artifact, chunked);
        stageOutputs.put("embed", embedded);
        
        // Stage 4: Index (depends on embed)
        StageOutput indexed = executeStage("index", artifact, embedded);
        stageOutputs.put("index", indexed);
        
        // Stage 5: Graph Update (depends on index)
        StageOutput graphUpdated = executeStage("graph_update", artifact, indexed);
        stageOutputs.put("graph_update", graphUpdated);
        
        long elapsed = System.currentTimeMillis() - startTime;
        
        // Cache results
        CacheEntry cacheEntry = new CacheEntry(
            artifact.getArtifactId(),
            inputHash,
            stageOutputs,
            System.currentTimeMillis()
        );
        cache.put(artifact.getArtifactId(), cacheEntry);
        
        result.outputs = stageOutputs;
        result.totalTimeMs = elapsed;
        result.cached = false;
        
        log.info("Indexing completed in {}ms: {}", elapsed, artifact.getArtifactId());
        
        return result;
    }
    
    /**
     * Execute single pipeline stage
     */
    private StageOutput executeStage(String stageName, Artifact artifact, 
                                     StageOutput dependency) throws Exception {
        PipelineNode node = nodes.get(stageName);
        if (node == null) {
            log.warn("Pipeline node not found: {}", stageName);
            return new StageOutput(stageName, null, 0);
        }
        
        long startTime = System.currentTimeMillis();
        
        Object input = dependency != null ? dependency.output : artifact;
        Object output = node.process(input);
        
        long elapsed = System.currentTimeMillis() - startTime;
        
        log.debug("Stage {} completed in {}ms", stageName, elapsed);
        
        return new StageOutput(stageName, output, elapsed);
    }
    
    /**
     * Batch indexing with DAG optimization
     */
    public List<IndexingResult> batchIndexing(List<Artifact> artifacts) throws Exception {
        List<CompletableFuture<IndexingResult>> futures = new ArrayList<>();
        
        for (Artifact artifact : artifacts) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return executeIndexing(artifact);
                } catch (Exception e) {
                    log.error("Indexing failed for artifact: {}", artifact.getArtifactId(), e);
                    throw new RuntimeException(e);
                }
            }, executor));
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .toList())
            .get();
    }
    
    /**
     * Invalidate cache for specific artifacts
     */
    public void invalidateCache(List<String> artifactIds) {
        for (String id : artifactIds) {
            cache.remove(id);
            log.debug("Invalidated cache: {}", id);
        }
    }
    
    /**
     * Get cache statistics
     */
    public CacheStatistics getStatistics() {
        CacheStatistics stats = new CacheStatistics();
        stats.totalCacheEntries = cache.size();
        stats.totalMemoryBytes = cache.values().stream()
            .mapToLong(e -> estimateSize(e))
            .sum();
        return stats;
    }
    
    private String computeHash(Artifact artifact) {
        try {
            String input = artifact.getArtifactId() + 
                         artifact.getContent() + 
                         artifact.getTimestamp();
            
            byte[] digest = hasher.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Failed to compute hash", e);
            return UUID.randomUUID().toString();
        }
    }
    
    private long estimateSize(CacheEntry entry) {
        // Rough estimate
        return entry.inputHash.length() + entry.outputs.values().stream()
            .mapToLong(o -> o.output != null ? o.output.toString().length() : 0)
            .sum();
    }
    
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // ===== Data Models =====
    
    @Data
    public static class IndexingResult {
        public String artifactId;
        public Map<String, StageOutput> outputs;
        public long totalTimeMs;
        public boolean cached;
        public long cacheTimeMs;
    }
    
    @Data
    public static class StageOutput {
        public String stageName;
        public Object output;
        public long elapsedMs;
        
        public StageOutput(String name, Object output, long elapsed) {
            this.stageName = name;
            this.output = output;
            this.elapsedMs = elapsed;
        }
    }
    
    @Data
    public static class CacheEntry {
        public String artifactId;
        public String inputHash;
        public Map<String, StageOutput> outputs;
        public long computedAt;
    }
    
    @Data
    public static class CacheStatistics {
        public int totalCacheEntries;
        public long totalMemoryBytes;
    }
    
    // ===== Interfaces =====
    
    public interface PipelineNode {
        /**
         * Process input and return output
         */
        Object process(Object input) throws Exception;
        
        /**
         * Get node name
         */
        String getName();
        
        /**
         * Get expected output type
         */
        Class<?> getOutputType();
    }
}
