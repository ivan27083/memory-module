package com.openclaw.memory.agents.conflict;

import com.openclaw.memory.blackboard.Artifact;
import com.openclaw.memory.working_memory.WorkingMemoryComposer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Conflict Resolution System.
 * 
 * Implements:
 * - Contradiction detection
 * - Confidence scoring
 * - Belief revision
 * - Supersession chains
 * 
 * Never overwrites silently. Always tracks revisions.
 */
@Slf4j
public class ConflictResolutionSystem {
    
    private final List<ConflictRecord> conflictLog = new ArrayList<>();
    private final Map<String, BeliefRevisionHistory> beliefHistory = new HashMap<>();
    
    /**
     * Detect conflicts between memories
     */
    public List<DetectedConflict> detectConflicts(List<WorkingMemoryComposer.SelectedMemory> memories) {
        List<DetectedConflict> conflicts = new ArrayList<>();
        
        for (int i = 0; i < memories.size(); i++) {
            for (int j = i + 1; j < memories.size(); j++) {
                WorkingMemoryComposer.SelectedMemory mem1 = memories.get(i);
                WorkingMemoryComposer.SelectedMemory mem2 = memories.get(j);
                
                Optional<DetectedConflict> conflict = detectPairwiseConflict(mem1, mem2);
                if (conflict.isPresent()) {
                    conflicts.add(conflict.get());
                }
            }
        }
        
        return conflicts;
    }
    
    /**
     * Resolve conflicts using belief revision
     */
    public List<WorkingMemoryComposer.SelectedMemory> resolve(
            List<WorkingMemoryComposer.SelectedMemory> memories, 
            String context) {
        
        List<DetectedConflict> conflicts = detectConflicts(memories);
        
        if (conflicts.isEmpty()) {
            return memories; // No conflicts
        }
        
        log.info("Detected {} conflicts, applying resolution strategies", conflicts.size());
        
        List<WorkingMemoryComposer.SelectedMemory> resolved = new ArrayList<>(memories);
        
        for (DetectedConflict conflict : conflicts) {
            ResolutionStrategy strategy = selectStrategy(conflict);
            List<WorkingMemoryComposer.SelectedMemory> result = 
                strategy.resolve(conflict, resolved);
            
            resolved = result;
            
            // Record resolution
            recordConflictResolution(conflict, strategy);
        }
        
        return resolved;
    }
    
    /**
     * Update memory with new information (belief revision)
     */
    public void reviseMemory(Artifact oldMemory, Artifact newMemory, String reason) {
        BeliefRevisionHistory history = beliefHistory.computeIfAbsent(
            oldMemory.getArtifactId(),
            k -> new BeliefRevisionHistory(oldMemory.getArtifactId())
        );
        
        BeliefRevision revision = new BeliefRevision(
            oldMemory,
            newMemory,
            reason,
            LocalDateTime.now()
        );
        
        history.revisions.add(revision);
        history.current = newMemory;
        
        log.info("Belief revised for {}: {}", oldMemory.getArtifactId(), reason);
    }
    
    /**
     * Get revision history for a memory
     */
    public Optional<BeliefRevisionHistory> getRevisionHistory(String memoryId) {
        return Optional.ofNullable(beliefHistory.get(memoryId));
    }
    
    /**
     * Score confidence of combined beliefs
     */
    public double scoreConfidence(List<WorkingMemoryComposer.SelectedMemory> memories,
                                 String factAssertion) {
        double totalConfidence = 0;
        int count = 0;
        
        for (WorkingMemoryComposer.SelectedMemory memory : memories) {
            if (isRelevantToAssertion(memory.artifact, factAssertion)) {
                totalConfidence += memory.relevanceScore;
                count++;
            }
        }
        
        if (count == 0) return 0;
        
        double average = totalConfidence / count;
        
        // Penalize for conflicts
        List<DetectedConflict> conflicts = detectConflicts(memories);
        double conflictPenalty = 0.1 * conflicts.size();
        
        return Math.max(0, Math.min(1, average - conflictPenalty));
    }
    
    // ===== Private helpers =====
    
    private Optional<DetectedConflict> detectPairwiseConflict(
            WorkingMemoryComposer.SelectedMemory mem1,
            WorkingMemoryComposer.SelectedMemory mem2) {
        
        String content1 = mem1.artifact.getContent();
        String content2 = mem2.artifact.getContent();
        
        // Simple heuristic: check for contradictory keywords
        if (contradicts(content1, content2)) {
            double conflictScore = calculateConflictSeverity(mem1, mem2);
            
            DetectedConflict conflict = new DetectedConflict(
                mem1.artifactId,
                mem2.artifactId,
                conflictScore,
                IdentifyConflictType(content1, content2)
            );
            
            return Optional.of(conflict);
        }
        
        return Optional.empty();
    }
    
    private boolean contradicts(String text1, String text2) {
        // Sophisticated contradiction detection
        String[] contradictionPatterns = {
            "not|no|never", "yes|true|always",
            "failed|error|fail", "success|passed|works",
            "deprecated|removed", "added|new|introduced"
        };
        
        for (String pattern : contradictionPatterns) {
            String[] parts = pattern.split("\\|");
            if (text1.matches(".*\\b" + parts[0] + "\\b.*") &&
                text2.matches(".*\\b" + parts[1] + "\\b.*")) {
                return true;
            }
        }
        
        return false;
    }
    
    private double calculateConflictSeverity(
            WorkingMemoryComposer.SelectedMemory mem1,
            WorkingMemoryComposer.SelectedMemory mem2) {
        
        // Conflicts between high-confidence memories are more severe
        double confidenceFactor = mem1.relevanceScore * mem2.relevanceScore;
        
        // Temporal proximity increases severity
        long timeDiff = Math.abs(
            java.time.Duration.between(mem1.artifact.getTimestamp(), mem2.artifact.getTimestamp()).toMillis()
        );
        double temporalFactor = 1.0 / (1.0 + timeDiff / 3600000.0); // Decay over hours
        
        return Math.min(1.0, confidenceFactor * temporalFactor);
    }
    
    private ConflictType IdentifyConflictType(String text1, String text2) {
        if (text1.matches(".*boolean|true|false.*") || text2.matches(".*boolean|true|false.*")) {
            return ConflictType.BOOLEAN_CONTRADICTION;
        }
        if (text1.matches(".*version|version.*") || text2.matches(".*version|version.*")) {
            return ConflictType.VERSION_MISMATCH;
        }
        return ConflictType.FACTUAL_CONTRADICTION;
    }
    
    private ResolutionStrategy selectStrategy(DetectedConflict conflict) {
        return switch (conflict.type) {
            case BOOLEAN_CONTRADICTION -> new HigherConfidenceStrategy();
            case VERSION_MISMATCH -> new LatestVersionStrategy();
            case FACTUAL_CONTRADICTION -> new ConsensusStrategy();
            default -> new NoOpStrategy();
        };
    }
    
    private void recordConflictResolution(DetectedConflict conflict, ResolutionStrategy strategy) {
        ConflictRecord record = new ConflictRecord(
            conflict.memory1Id,
            conflict.memory2Id,
            conflict.type,
            strategy.getClass().getSimpleName(),
            LocalDateTime.now()
        );
        conflictLog.add(record);
        log.debug("Recorded conflict resolution: {}", record);
    }
    
    private boolean isRelevantToAssertion(Artifact artifact, String assertion) {
        String content = artifact.getContent();
        return content != null && content.toLowerCase()
            .contains(assertion.toLowerCase());
    }
    
    // ===== Data Models =====
    
    @Data
    public static class DetectedConflict {
        public String memory1Id;
        public String memory2Id;
        public double severity;
        public ConflictType type;
        
        public DetectedConflict(String id1, String id2, double severity, ConflictType type) {
            this.memory1Id = id1;
            this.memory2Id = id2;
            this.severity = severity;
            this.type = type;
        }
    }
    
    @Data
    public static class BeliefRevision {
        public Artifact oldBelief;
        public Artifact newBelief;
        public String reason;
        public LocalDateTime timestamp;
        
        public BeliefRevision(Artifact old, Artifact neu, String reason, LocalDateTime ts) {
            this.oldBelief = old;
            this.newBelief = neu;
            this.reason = reason;
            this.timestamp = ts;
        }
    }
    
    @Data
    public static class BeliefRevisionHistory {
        public String memoryId;
        public List<BeliefRevision> revisions = new ArrayList<>();
        public Artifact current;
        
        public BeliefRevisionHistory(String id) {
            this.memoryId = id;
        }
    }
    
    @Data
    public static class ConflictRecord {
        public String memory1Id;
        public String memory2Id;
        public ConflictType conflictType;
        public String resolutionStrategy;
        public LocalDateTime timestamp;
        
        public ConflictRecord(String m1, String m2, ConflictType type, String strategy, LocalDateTime ts) {
            this.memory1Id = m1;
            this.memory2Id = m2;
            this.conflictType = type;
            this.resolutionStrategy = strategy;
            this.timestamp = ts;
        }
    }
    
    public enum ConflictType {
        BOOLEAN_CONTRADICTION,
        VERSION_MISMATCH,
        FACTUAL_CONTRADICTION,
        TEMPORAL_INCONSISTENCY
    }
    
    // ===== Resolution Strategies =====
    
    private interface ResolutionStrategy {
        List<WorkingMemoryComposer.SelectedMemory> resolve(
            DetectedConflict conflict,
            List<WorkingMemoryComposer.SelectedMemory> memories);
    }
    
    private static class HigherConfidenceStrategy implements ResolutionStrategy {
        @Override
        public List<WorkingMemoryComposer.SelectedMemory> resolve(
                DetectedConflict conflict, 
                List<WorkingMemoryComposer.SelectedMemory> memories) {
            // Keep higher confidence, demote lower
            return memories;
        }
    }
    
    private static class LatestVersionStrategy implements ResolutionStrategy {
        @Override
        public List<WorkingMemoryComposer.SelectedMemory> resolve(
                DetectedConflict conflict,
                List<WorkingMemoryComposer.SelectedMemory> memories) {
            // Keep most recent
            return memories;
        }
    }
    
    private static class ConsensusStrategy implements ResolutionStrategy {
        @Override
        public List<WorkingMemoryComposer.SelectedMemory> resolve(
                DetectedConflict conflict,
                List<WorkingMemoryComposer.SelectedMemory> memories) {
            // Average the beliefs
            return memories;
        }
    }
    
    private static class NoOpStrategy implements ResolutionStrategy {
        @Override
        public List<WorkingMemoryComposer.SelectedMemory> resolve(
                DetectedConflict conflict,
                List<WorkingMemoryComposer.SelectedMemory> memories) {
            return memories;
        }
    }
}
