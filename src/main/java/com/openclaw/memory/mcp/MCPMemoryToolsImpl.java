package com.openclaw.memory.mcp;

import com.openclaw.memory.blackboard.Artifact;
import com.openclaw.memory.blackboard.MemoryBlackboard;
import com.openclaw.memory.blackboard.Provenance;
import com.openclaw.memory.domain.model.RetrievalResult;
import com.openclaw.memory.retrieval.QMDRetrievalEngine;
import com.openclaw.memory.agents.conflict.ConflictResolutionAgent;
import com.openclaw.memory.working_memory.WorkingMemoryComposer;
import com.openclaw.memory.storage.ForgetSystem;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP Tools Implementation
 * 
 * PHASE 10 - MCP API Expansion
 * Bridges memory module to external agents via Model Context Protocol.
 * 
 * Implements all required MCP tools:
 * - memory.search (hybrid retrieval)
 * - memory.store (provenance-tracked ingestion)
 * - memory.update (belief revision)
 * - memory.delete (archival, never true deletion)
 * - memory.timeline (temporal queries)
 * - memory.conflicts (active contradictions)
 * - memory.explain (retrieval explainability)
 * - memory.forget (semantic compression)
 * - memory.pin (working memory pinning)
 * - memory.stat (system metrics)
 * 
 * @author Memory Module Team
 */
@Slf4j
public class MCPMemoryToolsImpl implements MCPMemoryTools.MCPToolImplementation {
    
    private final MemoryBlackboard blackboard;
    private final QMDRetrievalEngine retrievalEngine;
    private final ConflictResolutionAgent conflictAgent;
    private final WorkingMemoryComposer workingMemoryComposer;
    private final ForgetSystem forgetSystem;
    
    private final Map<String, Long> pinnedMemories = new HashMap<>();
    private long toolInvocations = 0;
    
    public MCPMemoryToolsImpl(MemoryBlackboard blackboard,
                            QMDRetrievalEngine retrievalEngine,
                            ConflictResolutionAgent conflictAgent,
                            WorkingMemoryComposer workingMemoryComposer,
                            ForgetSystem forgetSystem) {
        this.blackboard = blackboard;
        this.retrievalEngine = retrievalEngine;
        this.conflictAgent = conflictAgent;
        this.workingMemoryComposer = workingMemoryComposer;
        this.forgetSystem = forgetSystem;
        log.info("MCPMemoryToolsImpl initialized");
    }
    
    @Override
    public List<RetrievalResult> search(String query, MCPMemoryTools.SearchOptions options) {
        toolInvocations++;
        log.info("MCP search() called: query='{}', topK={}", query, options.topK);
        
        try {
            // Use QMD engine for hybrid retrieval
            List<RetrievalResult> results = retrievalEngine.search(
                query,
                options.topK,
                options.confidenceThreshold
            );
            
            // Filter by confidence if needed
            List<RetrievalResult> filtered = results.stream()
                .filter(r -> r.getScore() >= options.confidenceThreshold)
                .limit(options.topK)
                .collect(Collectors.toList());
            
            log.info("Search completed: {} results returned", filtered.size());
            return filtered;
            
        } catch (Exception e) {
            log.error("Search failed: {}", query, e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public Artifact store(String content, String contentType, String sourceAgent) {
        toolInvocations++;
        log.info("MCP store() called: type={}, source={}, contentLength={}",
                contentType, sourceAgent, content.length());
        
        try {
            // Create artifact with provenance
            String artifactId = UUID.randomUUID().toString();
            LocalDateTime now = LocalDateTime.now();
            
            // Create provenance
            Provenance provenance = new Provenance(
                artifactId,
                sourceAgent,
                List.of(artifactId), // source events
                now,
                0.9f // initial confidence
            );
            
            // Create artifact
            Artifact artifact = new Artifact(
                artifactId,
                content,
                contentType,
                now,
                provenance,
                new HashMap<>() // no parent yet
            );
            
            // Store in blackboard
            blackboard.storeArtifact(artifact);
            
            log.info("Artifact stored: {}", artifactId);
            return artifact;
            
        } catch (Exception e) {
            log.error("Store failed", e);
            return null;
        }
    }
    
    @Override
    public boolean update(String memoryId, String newContent, String reason) {
        toolInvocations++;
        log.info("MCP update() called: memoryId={}, reason={}", memoryId, reason);
        
        try {
            Optional<Artifact> existing = blackboard.getArtifact(memoryId);
            
            if (existing.isEmpty()) {
                log.warn("Memory not found: {}", memoryId);
                return false;
            }
            
            // Create new artifact (never overwrite)
            String newArtifactId = UUID.randomUUID().toString();
            LocalDateTime now = LocalDateTime.now();
            
            Provenance newProvenance = new Provenance(
                newArtifactId,
                "mcp_update",
                List.of(memoryId), // supersedes existing
                now,
                0.85f
            );
            
            Artifact newArtifact = new Artifact(
                newArtifactId,
                newContent,
                "updated_fact",
                now,
                newProvenance,
                Map.of("supersedes", memoryId, "reason", reason)
            );
            
            // Store new artifact
            blackboard.storeArtifact(newArtifact);
            
            // Mark old as superseded
            blackboard.updateArtifact(memoryId, a ->
                a.getProvenance().markSuperseded(newArtifactId)
            );
            
            log.info("Memory updated: {} -> {}", memoryId, newArtifactId);
            return true;
            
        } catch (Exception e) {
            log.error("Update failed: {}", memoryId, e);
            return false;
        }
    }
    
    @Override
    public boolean archive(String memoryId, String reason) {
        toolInvocations++;
        log.info("MCP archive() called: memoryId={}, reason={}", memoryId, reason);
        
        try {
            // Mark as archived in provenance (never delete)
            blackboard.updateArtifact(memoryId, a -> {
                Map<String, Object> metadata = new HashMap<>(a.getMetadata());
                metadata.put("archived", true);
                metadata.put("archivedReason", reason);
                metadata.put("archivedAt", LocalDateTime.now());
                return a;
            });
            
            log.info("Memory archived: {}", memoryId);
            return true;
            
        } catch (Exception e) {
            log.error("Archive failed: {}", memoryId, e);
            return false;
        }
    }
    
    @Override
    public List<Artifact> getTimeline(String query, LocalDateTime from, LocalDateTime to) {
        toolInvocations++;
        log.info("MCP timeline() called: query='{}', from={}, to={}",
                query, from, to);
        
        try {
            // Search and filter by temporal range
            List<RetrievalResult> results = retrievalEngine.search(query, 100, 0.0f);
            
            List<Artifact> timeline = new ArrayList<>();
            
            for (RetrievalResult result : results) {
                Optional<Artifact> artifact = blackboard.getArtifact(result.getMemoryId());
                
                if (artifact.isPresent()) {
                    Artifact a = artifact.get();
                    LocalDateTime t = a.getTimestamp();
                    
                    // Filter by temporal range
                    if (!t.isBefore(from) && !t.isAfter(to)) {
                        timeline.add(a);
                    }
                }
            }
            
            // Sort by timestamp
            timeline.sort(Comparator.comparing(Artifact::getTimestamp));
            
            log.info("Timeline retrieved: {} artifacts in range", timeline.size());
            return timeline;
            
        } catch (Exception e) {
            log.error("Timeline retrieval failed", e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public int getActiveConflicts() {
        toolInvocations++;
        log.info("MCP conflicts() called");
        
        try {
            // Get conflict statistics
            ConflictResolutionAgent.ConflictStats stats = 
                conflictAgent.getConflictStats();
            
            return (int) stats.unresolvedConflicts;
            
        } catch (Exception e) {
            log.error("Conflict retrieval failed", e);
            return 0;
        }
    }
    
    @Override
    public String getExplanation(String memoryId) {
        toolInvocations++;
        log.info("MCP explain() called: memoryId={}", memoryId);
        
        try {
            Optional<Artifact> artifact = blackboard.getArtifact(memoryId);
            
            if (artifact.isEmpty()) {
                return "Memory not found: " + memoryId;
            }
            
            Artifact a = artifact.get();
            StringBuilder explanation = new StringBuilder();
            
            // Build explanation
            explanation.append("Memory ID: ").append(memoryId).append("\n");
            explanation.append("Type: ").append(a.getContentType()).append("\n");
            explanation.append("Created: ").append(a.getTimestamp()).append("\n");
            explanation.append("Confidence: ").append(
                String.format("%.2f", a.getProvenance().getConfidenceScore())
            ).append("\n");
            explanation.append("Source: ").append(
                a.getProvenance().getSourceAgent()
            ).append("\n");
            explanation.append("Evidence: ").append(
                a.getProvenance().getSourceEventIds().size()
            ).append(" events\n");
            
            if (a.getMetadata().containsKey("supersedes")) {
                explanation.append("Supersedes: ").append(
                    a.getMetadata().get("supersedes")
                ).append("\n");
            }
            
            explanation.append("Content: ").append(
                a.getContent().substring(0, Math.min(200, a.getContent().length()))
            ).append("...\n");
            
            return explanation.toString();
            
        } catch (Exception e) {
            log.error("Explanation generation failed: {}", memoryId, e);
            return "Error generating explanation";
        }
    }
    
    @Override
    public long runForgetCycle(int percentileThreshold) {
        toolInvocations++;
        log.info("MCP forget() called: threshold={}%", percentileThreshold);
        
        try {
            // Run forgetting via ForgetSystem
            long archived = forgetSystem.runForgetCycle(percentileThreshold);
            
            log.info("Forget cycle completed: {} artifacts processed", archived);
            return archived;
            
        } catch (Exception e) {
            log.error("Forget cycle failed", e);
            return 0;
        }
    }
    
    @Override
    public boolean pin(String memoryId) {
        toolInvocations++;
        log.info("MCP pin() called: memoryId={}", memoryId);
        
        try {
            Optional<Artifact> artifact = blackboard.getArtifact(memoryId);
            
            if (artifact.isEmpty()) {
                log.warn("Cannot pin: memory not found");
                return false;
            }
            
            // Pin in working memory
            pinnedMemories.put(memoryId, System.currentTimeMillis());
            
            // Mark in metadata
            blackboard.updateArtifact(memoryId, a -> {
                Map<String, Object> metadata = new HashMap<>(a.getMetadata());
                metadata.put("pinned", true);
                metadata.put("pinnedAt", LocalDateTime.now());
                return a;
            });
            
            log.info("Memory pinned: {}", memoryId);
            return true;
            
        } catch (Exception e) {
            log.error("Pin failed: {}", memoryId, e);
            return false;
        }
    }
    
    @Override
    public Object getStatistics() {
        log.info("MCP stat() called");
        
        try {
            // Aggregate statistics
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalInvocations", toolInvocations);
            stats.put("pinnedMemories", pinnedMemories.size());
            stats.put("activeConflicts", getActiveConflicts());
            stats.put("timestamp", LocalDateTime.now());
            
            return stats;
            
        } catch (Exception e) {
            log.error("Statistics retrieval failed", e);
            return new HashMap<>();
        }
    }
}
