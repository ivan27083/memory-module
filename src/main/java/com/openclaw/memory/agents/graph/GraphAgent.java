package com.openclaw.memory.agents.graph;

import com.openclaw.memory.agents.BaseAgent;
import com.openclaw.memory.blackboard.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Graph Agent Interface (Temporal Reasoning)
 * 
 * Ответственность:
 * - Реализует граф Kuzu на основе временных ребер
 * - Управляет причинно-следственными связями
 * - Отслеживает валидность временных интервалов
 */
public interface GraphAgent extends BaseAgent {
    
    /**
     * Добавить узел в граф
     */
    void addNode(String nodeId, String nodeType, Map<String, Object> properties);
    
    /**
     * Добавить ребро с временной информацией
     */
    void addTemporalEdge(String fromNodeId, String toNodeId, String edgeType,
                        Instant validFrom, Instant validTo);
    
    /**
     * Получить причины события
     */
    List<String> getCauses(String nodeId);
    
    /**
     * Получить следствия события
     */
    List<String> getConsequences(String nodeId);
    
    /**
     * Трассировать временной путь между двумя событиями
     */
    TemporalPath findTemporalPath(String fromNodeId, String toNodeId);
    
    /**
     * Проверить временную согласованность пути
     */
    boolean isTemporallyConsistent(TemporalPath path);
    
    /**
     * Получить граф причинно-следственных связей
     */
    CausalGraph getCausalGraph();
    
    /**
     * Получить все события в заданное время
     */
    List<String> getNodesByTimePoint(Instant time);
    
    class TemporalPath {
        public final List<String> nodes;
        public final List<String> edges;
        public final Instant pathStart;
        public final Instant pathEnd;
        public final boolean isConsistent;
        
        public TemporalPath(List<String> nodes, List<String> edges,
                          Instant start, Instant end, boolean consistent) {
            this.nodes = nodes;
            this.edges = edges;
            this.pathStart = start;
            this.pathEnd = end;
            this.isConsistent = consistent;
        }
    }
    
    class CausalGraph {
        public final int nodeCount;
        public final int edgeCount;
        public final List<String> rootCauses;
        public final List<String> finalEffects;
        
        public CausalGraph(int nodes, int edges, List<String> roots, List<String> effects) {
            this.nodeCount = nodes;
            this.edgeCount = edges;
            this.rootCauses = roots;
            this.finalEffects = effects;
        }
    }
}
