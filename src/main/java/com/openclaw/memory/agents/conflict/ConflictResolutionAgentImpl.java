package com.openclaw.memory.agents.conflict;

import com.openclaw.memory.agents.BaseAgent;
import com.openclaw.memory.blackboard.Artifact;
import com.openclaw.memory.blackboard.MemoryBlackboard;
import com.openclaw.memory.graph.TemporalGraphManager;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Conflict Resolution Agent Implementation
 * 
 * PHASE 5 - Conflict & Belief System
 * Prevents hallucination amplification through:
 * - Contradiction detection (semantic, temporal, confidence)
 * - Confidence arbitration
 * - Supersession graph tracking
 * - Belief evolution without silent overwrites
 * 
 * @author Memory Module Team
 */
@Slf4j
public class ConflictResolutionAgentImpl implements ConflictResolutionAgent, BaseAgent {
    
    private final MemoryBlackboard blackboard;
    private final TemporalGraphManager graphManager;
    
    private final Map<String, Contradiction> unresolvedContradictions = new ConcurrentHashMap<>();
    private final Map<String, Resolution> resolvedContradictions = new ConcurrentHashMap<>();
    private final Map<String, BeliefNode> beliefGraph = new ConcurrentHashMap<>();
    
    private long contradictionsDetected = 0;
    private long successfulResolutions = 0;
    
    public ConflictResolutionAgentImpl(MemoryBlackboard blackboard, 
                                       TemporalGraphManager graphManager) {
        this.blackboard = blackboard;
        this.graphManager = graphManager;
        log.info("ConflictResolutionAgentImpl initialized");
    }
    
    @Override
    public List<Contradiction> detectContradictions(List<String> artifactIds) {
        List<Contradiction> contradictions = new ArrayList<>();
        
        // Get all artifacts
        Map<String, Artifact> artifacts = new HashMap<>();
        for (String id : artifactIds) {
            Optional<Artifact> artifact = blackboard.getArtifact(id);
            artifact.ifPresent(a -> artifacts.put(id, a));
        }
        
        // Pairwise comparison
        String[] ids = artifacts.keySet().toArray(new String[0]);
        for (int i = 0; i < ids.length; i++) {
            for (int j = i + 1; j < ids.length; j++) {
                Artifact a1 = artifacts.get(ids[i]);
                Artifact a2 = artifacts.get(ids[j]);
                
                Optional<Contradiction> contradiction = detectPairwise(a1, a2);
                contradiction.ifPresent(c -> {
                    contradictions.add(c);
                    unresolvedContradictions.put(c.contradictionId, c);
                    log.warn("Contradiction detected: {} (severity: {})", 
                            c.contradictionId, c.severity);
                });
            }
        }
        
        this.contradictionsDetected += contradictions.size();
        return contradictions;
    }
    
    /**
     * Detect pairwise contradiction between two artifacts
     */
    private Optional<Contradiction> detectPairwise(Artifact a1, Artifact a2) {
        // Type 1: Direct semantic conflict
        if (isSemanticConflict(a1, a2)) {
            return Optional.of(new Contradiction(
                UUID.randomUUID().toString(),
                a1.getArtifactId(),
                a2.getArtifactId(),
                "Semantic contradiction: conflicting factual content",
                Contradiction.ContradictionType.DIRECT_CONFLICT,
                calculateSemanticSimilarity(a1, a2)
            ));
        }
        
        // Type 2: Temporal anomaly
        if (isTemporalAnomaly(a1, a2)) {
            return Optional.of(new Contradiction(
                UUID.randomUUID().toString(),
                a1.getArtifactId(),
                a2.getArtifactId(),
                "Temporal anomaly: violates causal ordering",
                Contradiction.ContradictionType.TEMPORAL_ANOMALY,
                0.8f
            ));
        }
        
        // Type 3: Confidence inversion
        if (isConfidenceInversion(a1, a2)) {
            return Optional.of(new Contradiction(
                UUID.randomUUID().toString(),
                a1.getArtifactId(),
                a2.getArtifactId(),
                "Confidence inversion: contradictory confidence levels",
                Contradiction.ContradictionType.CONFIDENCE_INVERSION,
                0.6f
            ));
        }
        
        return Optional.empty();
    }
    
    private boolean isSemanticConflict(Artifact a1, Artifact a2) {
        // Check if content semantically contradicts
        String content1 = a1.getContent().toLowerCase();
        String content2 = a2.getContent().toLowerCase();
        
        // Simple heuristic: look for explicit contradictions
        if (containsNegation(content1, content2) || containsNegation(content2, content1)) {
            return true;
        }
        
        // More sophisticated: could use embedding similarity
        return false;
    }
    
    private boolean containsNegation(String text1, String text2) {
        return (text1.contains("not") && text1.contains(extractKey(text2))) ||
               (text1.contains("false") && text1.contains(extractKey(text2))) ||
               (text1.contains("never") && text1.contains(extractKey(text2)));
    }
    
    private String extractKey(String text) {
        // Extract first noun/key term
        return text.split("\\s+")[0];
    }
    
    private boolean isTemporalAnomaly(Artifact a1, Artifact a2) {
        LocalDateTime t1 = a1.getTimestamp();
        LocalDateTime t2 = a2.getTimestamp();
        
        // Check if temporal ordering violates expected causality
        // (This is simplified; full implementation would use graph analysis)
        return false;
    }
    
    private boolean isConfidenceInversion(Artifact a1, Artifact a2) {
        // If two related facts have inverted confidence levels, this is anomalous
        float conf1 = a1.getProvenance().getConfidenceScore();
        float conf2 = a2.getProvenance().getConfidenceScore();
        
        // Check if both claim opposite things with high confidence
        return (conf1 > 0.8f && conf2 > 0.8f && 
                Math.abs(conf1 - conf2) > 0.3f);
    }
    
    private float calculateSemanticSimilarity(Artifact a1, Artifact a2) {
        // Simplified similarity; would use embeddings in production
        String c1 = a1.getContent().toLowerCase();
        String c2 = a2.getContent().toLowerCase();
        
        Set<String> words1 = new HashSet<>(Arrays.asList(c1.split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(c2.split("\\s+")));
        
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);
        
        return (float) intersection.size() / 
               Math.max(words1.size(), words2.size());
    }
    
    @Override
    public Resolution resolveContradiction(Contradiction contradiction) {
        log.info("Resolving contradiction: {}", contradiction.contradictionId);
        
        // Get the two conflicting artifacts
        Optional<Artifact> artifact1 = blackboard.getArtifact(contradiction.artifact1);
        Optional<Artifact> artifact2 = blackboard.getArtifact(contradiction.artifact2);
        
        if (artifact1.isEmpty() || artifact2.isEmpty()) {
            log.warn("Cannot resolve: artifacts not found");
            return null;
        }
        
        // Apply resolution strategy based on contradiction type
        String winningArtifactId;
        String reason;
        
        switch (contradiction.type) {
            case TEMPORAL_ANOMALY:
                // Winner is the one with consistent temporal ordering
                winningArtifactId = resolveTemporalConflict(
                    artifact1.get(), artifact2.get());
                reason = "Temporal consistency restored";
                break;
                
            case CONFIDENCE_INVERSION:
                // Winner is the one with higher evidence count
                winningArtifactId = resolveByEvidence(
                    artifact1.get(), artifact2.get());
                reason = "Resolved by evidence count";
                break;
                
            case DIRECT_CONFLICT:
            default:
                // Winner is most recent with sufficient confidence
                winningArtifactId = resolveByRecency(
                    artifact1.get(), artifact2.get());
                reason = "Resolved by recency";
                break;
        }
        
        // Create resolution record
        float confidence = 1.0f - (contradiction.severity * 0.2f); // Reduce by severity
        Resolution resolution = new Resolution(
            UUID.randomUUID().toString(),
            contradiction,
            winningArtifactId,
            reason,
            confidence
        );
        
        // Update tracking
        resolvedContradictions.put(resolution.resolutionId, resolution);
        unresolvedContradictions.remove(contradiction.contradictionId);
        successfulResolutions++;
        
        // Mark loser as superseded
        String loserArtifactId = winningArtifactId.equals(contradiction.artifact1) ? 
            contradiction.artifact2 : contradiction.artifact1;
        
        blackboard.updateArtifact(loserArtifactId, a -> 
            a.getProvenance().markSuperseded(winningArtifactId)
        );
        
        log.info("Resolution complete: {} wins (confidence: {})", 
                winningArtifactId, confidence);
        
        return resolution;
    }
    
    private String resolveTemporalConflict(Artifact a1, Artifact a2) {
        // Choose the one that maintains causal consistency
        boolean a1Consistent = graphManager.isConsistent(a1, LocalDateTime.now());
        boolean a2Consistent = graphManager.isConsistent(a2, LocalDateTime.now());
        
        return a1Consistent ? a1.getArtifactId() : a2.getArtifactId();
    }
    
    private String resolveByEvidence(Artifact a1, Artifact a2) {
        int evidence1 = a1.getProvenance().getSourceEventIds().size();
        int evidence2 = a2.getProvenance().getSourceEventIds().size();
        
        return evidence1 >= evidence2 ? a1.getArtifactId() : a2.getArtifactId();
    }
    
    private String resolveByRecency(Artifact a1, Artifact a2) {
        LocalDateTime t1 = a1.getTimestamp();
        LocalDateTime t2 = a2.getTimestamp();
        
        if (t1.isAfter(t2) && a1.getProvenance().getConfidenceScore() > 0.5f) {
            return a1.getArtifactId();
        }
        if (t2.isAfter(t1) && a2.getProvenance().getConfidenceScore() > 0.5f) {
            return a2.getArtifactId();
        }
        
        return t1.isAfter(t2) ? a1.getArtifactId() : a2.getArtifactId();
    }
    
    @Override
    public List<Resolution> getResolvedContradictions() {
        return new ArrayList<>(resolvedContradictions.values());
    }
    
    @Override
    public List<Contradiction> getUnresolvedContradictions() {
        return new ArrayList<>(unresolvedContradictions.values());
    }
    
    @Override
    public boolean hasCyclicDependencies() {
        // Use DFS to detect cycles in belief graph
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        
        for (String node : beliefGraph.keySet()) {
            if (!visited.contains(node)) {
                if (hasCycleDFS(node, visited, recursionStack)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private boolean hasCycleDFS(String node, Set<String> visited, Set<String> recursionStack) {
        visited.add(node);
        recursionStack.add(node);
        
        BeliefNode bn = beliefGraph.get(node);
        if (bn != null) {
            for (String dependent : bn.dependents) {
                if (!visited.contains(dependent)) {
                    if (hasCycleDFS(dependent, visited, recursionStack)) {
                        return true;
                    }
                } else if (recursionStack.contains(dependent)) {
                    return true;
                }
            }
        }
        
        recursionStack.remove(node);
        return false;
    }
    
    @Override
    public BeliefGraph getBeliefGraph() {
        List<String> cycles = new ArrayList<>();
        
        for (String node : beliefGraph.keySet()) {
            if (hasCyclicDependencies()) {
                cycles.add(node);
            }
        }
        
        return new BeliefGraph(
            beliefGraph.size(),
            beliefGraph.values().stream()
                .mapToInt(bn -> bn.dependents.size())
                .sum(),
            new ArrayList<>(beliefGraph.keySet()),
            cycles
        );
    }
    
    @Override
    public ConflictStats getConflictStats() {
        long unresolved = unresolvedContradictions.size();
        long resolved = resolvedContradictions.size();
        long total = contradictionsDetected;
        
        double avgSeverity = unresolvedContradictions.values().stream()
            .mapToDouble(c -> c.severity)
            .average()
            .orElse(0.0);
        
        double successRate = total > 0 ? 
            (double) successfulResolutions / total : 0.0;
        
        return new ConflictStats(
            total,
            resolved,
            unresolved,
            avgSeverity,
            successRate
        );
    }
    
    @Override
    public String getAgentName() {
        return "ConflictResolutionAgent";
    }
    
    @Override
    public String getAgentType() {
        return "CONFLICT";
    }
    
    @Override
    public void initialize() {
        log.info("ConflictResolutionAgent initialization complete");
    }
    
    @Override
    public void shutdown() {
        log.info("ConflictResolutionAgent shutdown: {} contradictions detected, " +
                "{} resolved", contradictionsDetected, successfulResolutions);
    }
    
    // Inner class for belief graph tracking
    @Data
    private static class BeliefNode {
        private final String artifactId;
        private final Set<String> dependents = new HashSet<>();
        private final Set<String> dependencies = new HashSet<>();
        private LocalDateTime lastUpdated;
        
        public BeliefNode(String artifactId) {
            this.artifactId = artifactId;
            this.lastUpdated = LocalDateTime.now();
        }
    }
}
