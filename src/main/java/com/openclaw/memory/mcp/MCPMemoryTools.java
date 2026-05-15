package com.openclaw.memory.mcp;

import com.openclaw.memory.blackboard.Artifact;
import com.openclaw.memory.domain.model.RetrievalResult;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * MCP API - Model Context Protocol Tools for Memory Module.
 * 
 * Exposes memory operations as MCP tools:
 * - memory.search
 * - memory.store
 * - memory.update
 * - memory.delete
 * - memory.timeline
 * - memory.conflicts
 * - memory.explain
 * - memory.forget
 * - memory.pin
 */
@Slf4j
public class MCPMemoryTools {
    
    private final MCPToolImplementation implementation;
    
    public MCPMemoryTools(MCPToolImplementation impl) {
        this.implementation = impl;
    }
    
    /**
     * Tool: memory.search
     * Search across all memory layers using hybrid retrieval
     */
    public MemorySearchResult search(String query, SearchOptions options) {
        log.info("MCP Tool: memory.search(query={})", query);
        
        MemorySearchResult result = new MemorySearchResult(query);
        result.results = implementation.search(query, options);
        result.timestampMs = System.currentTimeMillis();
        
        return result;
    }
    
    /**
     * Tool: memory.store
     * Store new memory with provenance
     */
    public MemoryStoreResult store(String content, String contentType, String sourceAgent) {
        log.info("MCP Tool: memory.store(type={}, source={})", contentType, sourceAgent);
        
        Artifact artifact = implementation.store(content, contentType, sourceAgent);
        
        return new MemoryStoreResult(artifact.getArtifactId(), artifact.getTimestamp());
    }
    
    /**
     * Tool: memory.update
     * Update existing memory (creates supersession chain)
     */
    public MemoryUpdateResult update(String memoryId, String newContent, String reason) {
        log.info("MCP Tool: memory.update(memoryId={}, reason={})", memoryId, reason);
        
        boolean success = implementation.update(memoryId, newContent, reason);
        
        return new MemoryUpdateResult(memoryId, success, reason);
    }
    
    /**
     * Tool: memory.delete
     * Archive (never truly delete) memory
     */
    public MemoryDeleteResult delete(String memoryId, String reason) {
        log.info("MCP Tool: memory.delete(memoryId={}, reason={})", memoryId, reason);
        
        boolean archived = implementation.archive(memoryId, reason);
        
        return new MemoryDeleteResult(memoryId, archived, "Memory archived (not deleted)");
    }
    
    /**
     * Tool: memory.timeline
     * Retrieve memories in temporal order
     */
    public TimelineResult timeline(String query, LocalDateTime from, LocalDateTime to) {
        log.info("MCP Tool: memory.timeline(query={}, from={}, to={})", query, from, to);
        
        List<Artifact> timeline = implementation.getTimeline(query, from, to);
        
        return new TimelineResult(query, from, to, timeline);
    }
    
    /**
     * Tool: memory.conflicts
     * Get active conflicts in memory
     */
    public ConflictsResult getConflicts() {
        log.info("MCP Tool: memory.conflicts()");
        
        return new ConflictsResult(implementation.getActiveConflicts());
    }
    
    /**
     * Tool: memory.explain
     * Get explanation for a retrieval result
     */
    public ExplanationResult explain(String memoryId) {
        log.info("MCP Tool: memory.explain(memoryId={})", memoryId);
        
        return new ExplanationResult(
            memoryId,
            implementation.getExplanation(memoryId)
        );
    }
    
    /**
     * Tool: memory.forget
     * Run forgetting cycle (semantic compression + archival)
     */
    public ForgetResult forget(int percentileThreshold) {
        log.info("MCP Tool: memory.forget(threshold={})", percentileThreshold);
        
        return new ForgetResult(implementation.runForgetCycle(percentileThreshold));
    }
    
    /**
     * Tool: memory.pin
     * Pin memory to working memory (prevent eviction)
     */
    public PinResult pin(String memoryId) {
        log.info("MCP Tool: memory.pin(memoryId={})", memoryId);
        
        boolean pinned = implementation.pin(memoryId);
        
        return new PinResult(memoryId, pinned);
    }
    
    /**
     * Tool: memory.stat
     * Get memory system statistics
     */
    public StatisticsResult statistics() {
        log.info("MCP Tool: memory.stat()");
        
        return new StatisticsResult(implementation.getStatistics());
    }
    
    // ===== Data Models =====
    
    @Data
    public static class SearchOptions {
        public int topK = 10;
        public double confidenceThreshold = 0.5;
        public boolean includeExplanation = true;
    }
    
    @Data
    public static class MemorySearchResult {
        public String query;
        public List<RetrievalResult> results;
        public long timestampMs;
        
        public MemorySearchResult(String query) {
            this.query = query;
        }
    }
    
    @Data
    public static class MemoryStoreResult {
        public String memoryId;
        public LocalDateTime createdAt;
        
        public MemoryStoreResult(String id, LocalDateTime created) {
            this.memoryId = id;
            this.createdAt = created;
        }
    }
    
    @Data
    public static class MemoryUpdateResult {
        public String memoryId;
        public boolean success;
        public String reason;
        
        public MemoryUpdateResult(String id, boolean success, String reason) {
            this.memoryId = id;
            this.success = success;
            this.reason = reason;
        }
    }
    
    @Data
    public static class MemoryDeleteResult {
        public String memoryId;
        public boolean archived;
        public String message;
        
        public MemoryDeleteResult(String id, boolean archived, String msg) {
            this.memoryId = id;
            this.archived = archived;
            this.message = msg;
        }
    }
    
    @Data
    public static class TimelineResult {
        public String query;
        public LocalDateTime from;
        public LocalDateTime to;
        public List<Artifact> memories;
        
        public TimelineResult(String query, LocalDateTime from, LocalDateTime to, List<Artifact> mems) {
            this.query = query;
            this.from = from;
            this.to = to;
            this.memories = mems;
        }
    }
    
    @Data
    public static class ConflictsResult {
        public int activeConflicts;
        public List<String> details;
        
        public ConflictsResult(int count) {
            this.activeConflicts = count;
            this.details = new java.util.ArrayList<>();
        }
    }
    
    @Data
    public static class ExplanationResult {
        public String memoryId;
        public String explanation;
        
        public ExplanationResult(String id, String exp) {
            this.memoryId = id;
            this.explanation = exp;
        }
    }
    
    @Data
    public static class ForgetResult {
        public String status;
        public long processedCount;
        
        public ForgetResult(long count) {
            this.status = "completed";
            this.processedCount = count;
        }
    }
    
    @Data
    public static class PinResult {
        public String memoryId;
        public boolean pinned;
        
        public PinResult(String id, boolean pinned) {
            this.memoryId = id;
            this.pinned = pinned;
        }
    }
    
    @Data
    public static class StatisticsResult {
        public long totalMemories;
        public long tier1Size;
        public long tier2Size;
        public long tier3Size;
        public double cacheHitRatio;
        
        public StatisticsResult(Object stats) {
            // Stats aggregation from impl
        }
    }
    
    // ===== Interface =====
    
    public interface MCPToolImplementation {
        List<RetrievalResult> search(String query, SearchOptions options);
        Artifact store(String content, String contentType, String sourceAgent);
        boolean update(String memoryId, String newContent, String reason);
        boolean archive(String memoryId, String reason);
        List<Artifact> getTimeline(String query, LocalDateTime from, LocalDateTime to);
        int getActiveConflicts();
        String getExplanation(String memoryId);
        long runForgetCycle(int percentileThreshold);
        boolean pin(String memoryId);
        Object getStatistics();
    }
}
