package com.openclaw.memory.agents.conflict;

import com.openclaw.memory.agents.BaseAgent;
import com.openclaw.memory.blackboard.*;
import java.util.List;

/**
 * Conflict Resolution Agent Interface
 * 
 * Ответственность:
 * - Обнаруживает противоречия
 * - Поддерживает граф убеждений
 * - Управляет арбитражем уверенности
 * - Отслеживает цепи замещения
 */
public interface ConflictResolutionAgent extends BaseAgent {
    
    /**
     * Обнаружить противоречия между артефактами
     */
    List<Contradiction> detectContradictions(List<String> artifactIds);
    
    /**
     * Разрешить противоречие
     */
    Resolution resolveContradiction(Contradiction contradiction);
    
    /**
     * Получить ранее разрешенные противоречия
     */
    List<Resolution> getResolvedContradictions();
    
    /**
     * Получить неразрешенные противоречия
     */
    List<Contradiction> getUnresolvedContradictions();
    
    /**
     * Проверить, есть ли циклические зависимости убеждений
     */
    boolean hasCyclicDependencies();
    
    /**
     * Получить граф убеждений
     */
    BeliefGraph getBeliefGraph();
    
    /**
     * Получить статистику конфликтов
     */
    ConflictStats getConflictStats();
    
    class Contradiction {
        public final String contradictionId;
        public final String artifact1;
        public final String artifact2;
        public final String description;
        public final ContradictionType type;
        public final float severity; // 0-1
        
        public enum ContradictionType {
            DIRECT_CONFLICT,
            TEMPORAL_ANOMALY,
            CONFIDENCE_INVERSION,
            SUPERSESSION_CYCLE
        }
        
        public Contradiction(String id, String art1, String art2, String desc,
                           ContradictionType type, float severity) {
            this.contradictionId = id;
            this.artifact1 = art1;
            this.artifact2 = art2;
            this.description = desc;
            this.type = type;
            this.severity = severity;
        }
    }
    
    class Resolution {
        public final String resolutionId;
        public final Contradiction contradiction;
        public final String winningArtifact;
        public final String reason;
        public final float confidence;
        
        public Resolution(String id, Contradiction contra, String winner,
                        String reason, float confidence) {
            this.resolutionId = id;
            this.contradiction = contra;
            this.winningArtifact = winner;
            this.reason = reason;
            this.confidence = confidence;
        }
    }
    
    class BeliefGraph {
        public final int nodeCount;
        public final int edgeCount;
        public final List<String> stronglyConnectedComponents;
        public final List<String> beliefCycles;
        
        public BeliefGraph(int nodes, int edges, List<String> components,
                         List<String> cycles) {
            this.nodeCount = nodes;
            this.edgeCount = edges;
            this.stronglyConnectedComponents = components;
            this.beliefCycles = cycles;
        }
    }
    
    class ConflictStats {
        public final long totalConflictsDetected;
        public final long resolvedConflicts;
        public final long unresolvedConflicts;
        public final double averageSeverity;
        public final double resolutionSuccessRate;
        
        public ConflictStats(long total, long resolved, long unresolved,
                           double avgSeverity, double successRate) {
            this.totalConflictsDetected = total;
            this.resolvedConflicts = resolved;
            this.unresolvedConflicts = unresolved;
            this.averageSeverity = avgSeverity;
            this.resolutionSuccessRate = successRate;
        }
    }
}
