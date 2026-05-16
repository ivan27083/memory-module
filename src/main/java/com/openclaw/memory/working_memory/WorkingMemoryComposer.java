package com.openclaw.memory.working_memory;

import com.openclaw.memory.blackboard.Artifact;
import com.openclaw.memory.blackboard.MemoryBlackboard;
import com.openclaw.memory.retrieval.QMDRetrievalEngine;
import com.openclaw.memory.retrieval.RetrievalResult;
import com.openclaw.memory.retrieval.Retriever;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Working Memory Composer - Reconstructs context for execution.
 * 
 * Responsible for:
 * - Context reconstruction from retrieved memories
 * - Relevance filtering
 * - Contradiction resolution
 * - Final prompt assembly
 * 
 * Output includes selection explanation and causal chains.
 */
@Slf4j
public class WorkingMemoryComposer {
    public String content;
    private final Retriever retriever;
    private final ConflictResolver conflictResolver;
    private final TemporalResolver temporalResolver;
    private final int maxContextTokens;
    private final LocalDateTime currentTime;
    
    public WorkingMemoryComposer(Retriever retriever,
                                com.openclaw.memory.graph.TemporalGraphManager graphManager,
                                com.openclaw.memory.agents.conflict.ConflictResolutionAgent conflictAgent) {
        this(retriever,
             (memories, context) -> memories,
             (artifact, atTime) -> graphManager == null || graphManager.isConsistent(artifact, atTime),
             4000);
    }

    public WorkingMemoryComposer(Retriever retriever, 
                                ConflictResolver conflictResolver,
                                TemporalResolver temporalResolver,
                                int maxContextTokens) {
        this.retriever = retriever;
        this.conflictResolver = conflictResolver;
        this.temporalResolver = temporalResolver;
        this.maxContextTokens = maxContextTokens;
        this.currentTime = LocalDateTime.now();
    }
    
    /**
     * Compose working memory context for a given query
     */
    public WorkingMemoryContext compose(String query, CompositionOptions options) {
        long startTime = System.currentTimeMillis();
        
        log.info("Composing working memory for query: {}", query);
        
        // 1. Retrieve candidate memories
        List<RetrievalResult> candidates =
            retriever.search(query, options.maxCandidates).join();
        
        List<SelectedMemory> selectedMemories = new ArrayList<>();
        
        for (RetrievalResult result : candidates) {
            if (selectedMemories.size() >= options.maxMemoriesPerContext) break;

            if (!temporalResolver.isValid(result.artifact(), currentTime)) {
                log.debug("Skipping memory {} - not valid at current time", result.memoryId());
                continue;
            }
            if (result.score() < options.confidenceThreshold) {
                log.debug("Skipping memory {} - confidence below threshold", result.memoryId());
                continue;
            }

            SelectedMemory selected = new SelectedMemory(
                result.artifact(),
                result.score(),
                SelectionReason.RELEVANCE_MATCH,
                result.memoryId()
            );
            selected.retrievalExplanation = result;
            selectedMemories.add(selected);
        }
        
        // 3. Resolve conflicts
        List<SelectedMemory> resolvedMemories = conflictResolver.resolve(selectedMemories, query);
        
        // 4. Build causal chains
        Map<String, List<String>> causalChains = buildCausalChains(resolvedMemories);
        
        // 5. Assemble prompt
        String composedContext = assemblePrompt(resolvedMemories, causalChains, options);
        
        long elapsed = System.currentTimeMillis() - startTime;
        
        return new WorkingMemoryContext(
            query,
            resolvedMemories,
            composedContext,
            causalChains,
            new CompositionMetadata(elapsed, options.maxMemoriesPerContext, 
                                   resolvedMemories.size(), 0L)
        );
    }
    
    public WorkingMemoryContext composeContext(String query) {
        return compose(query, new CompositionOptions());
    }

    /**
     * Build causal chains linking memories
     */
    private Map<String, List<String>> buildCausalChains(List<SelectedMemory> memories) {
        Map<String, List<String>> chains = new HashMap<>();
        
        // Group by event dependencies
        for (SelectedMemory memory : memories) {
            Artifact artifact = memory.artifact;
            List<String> sources = new ArrayList<>();
            
            if (artifact.getProvenance() != null && 
                artifact.getProvenance().getSourceEventIds() != null) {
                sources.addAll(artifact.getProvenance().getSourceEventIds());
            }
            
            chains.put(artifact.getArtifactId(), sources);
        }
        
        return chains;
    }
    
    /**
     * Assemble final prompt from selected memories
     */
    private String assemblePrompt(List<SelectedMemory> memories,
                                  Map<String, List<String>> causalChains,
                                  CompositionOptions options) {
        StringBuilder sb = new StringBuilder();
        
        int tokenCount = 0;
        
        sb.append("# Working Memory Context\n\n");
        
        for (SelectedMemory memory : memories) {
            if (tokenCount >= maxContextTokens) {
                log.info("Reached token limit, stopping context assembly");
                break;
            }
            
            Artifact artifact = memory.artifact;
            
            sb.append("## Memory: ").append(artifact.getArtifactId()).append("\n");
            sb.append("- Type: ").append(artifact.getType()).append("\n");
            sb.append("- Confidence: ").append(String.format("%.2f", memory.relevanceScore)).append("\n");
            sb.append("- Reason: ").append(memory.selectionReason).append("\n");
            
            if (artifact.getProvenance() != null) {
                sb.append("- Source: ").append(artifact.getProvenance().getSourceAgent()).append("\n");
                sb.append("- Timestamp: ").append(artifact.getProvenance().getTimestamp()).append("\n");
            }
            
            String content = memory.artifact != null
                ? memory.artifact.getContent()
                : memory.content;
            sb.append("- Content: ").append(content).append("\n\n");
            
            tokenCount += estimateTokens(artifact.getContent());
        }
        
        if (!causalChains.isEmpty() && options.includeExplanation) {
            sb.append("\n# Causal Dependencies\n\n");
            for (Map.Entry<String, List<String>> entry : causalChains.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    sb.append("- ").append(entry.getKey()).append(" depends on: ")
                      .append(String.join(", ", entry.getValue())).append("\n");
                }
            }
        }
        
        return sb.toString();
    }
    
    private int estimateTokens(String text) {
        // Rough estimate: 1 token ≈ 4 characters
        return (text != null ? text.length() : 0) / 4;
    }
    
    // ===== Data Models =====
    
    @Data
    public static class WorkingMemoryContext {
        private String originalQuery;
        private List<SelectedMemory> selectedMemories;
        private String composedPrompt;
        private Map<String, List<String>> causalChains;
        private CompositionMetadata metadata;

        public WorkingMemoryContext(String originalQuery, List<SelectedMemory> selectedMemories,
                                    String composedPrompt, Map<String, List<String>> causalChains,
                                    CompositionMetadata metadata) {
            this.originalQuery = originalQuery;
            this.selectedMemories = selectedMemories;
            this.composedPrompt = composedPrompt;
            this.causalChains = causalChains;
            this.metadata = metadata;
        }

        public List<SelectedMemory> getSelectedMemories() { return selectedMemories; }
        public String getComposedPrompt() { return composedPrompt; }
        public Map<String, List<String>> getCausalChains() { return causalChains; }
        public CompositionMetadata getMetadata() { return metadata; }
    }
    
    @Data
    public static class SelectedMemory {
        public Artifact artifact;
        public double relevanceScore;
        public SelectionReason selectionReason;
        public String artifactId;
        public String content;
        public RetrievalResult retrievalExplanation;
        
        public SelectedMemory(Artifact artifact, double score, SelectionReason reason, String id) {
            this.artifact = artifact;
            this.relevanceScore = score;
            this.selectionReason = reason;
            this.artifactId = id;
        }
    }
    
    public enum SelectionReason {
        RELEVANCE_MATCH,
        CAUSAL_DEPENDENCY,
        TEMPORAL_PROXIMITY,
        CONFLICT_RESOLUTION
    }
    
    @Data
    public static class CompositionMetadata {
        public long totalTimeMs;
        public int maxMemoriesRequested;
        public int memoriesSelected;
        public long retrievalTimeMs;

        public CompositionMetadata(long totalTimeMs, int maxMemoriesRequested, int memoriesSelected, long retrievalTimeMs) {
            this.totalTimeMs = totalTimeMs;
            this.maxMemoriesRequested = maxMemoriesRequested;
            this.memoriesSelected = memoriesSelected;
            this.retrievalTimeMs = retrievalTimeMs;
        }
    }
    
    @Data
    public static class CompositionOptions {
        public int maxMemoriesPerContext = 20;
        public int maxCandidates = 100;
        public double confidenceThreshold = 0.5;
        public boolean includeExplanation = true;
    }
    
    // ===== Interfaces =====
    
    public interface ConflictResolver {
        List<SelectedMemory> resolve(List<SelectedMemory> memories, String context);
    }
    
    public interface TemporalResolver {
        boolean isValid(Artifact artifact, LocalDateTime atTime);
    }
}
