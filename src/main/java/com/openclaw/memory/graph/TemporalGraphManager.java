package com.openclaw.memory.graph;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Temporal Graph Manager - Supports valid_from/valid_to for all edges.
 * 
 * Enables:
 * - Time-aware traversal
 * - Supersession chain tracking
 * - Causal dependency queries at specific points in time
 * - Belief revision history
 */
@Slf4j
public class TemporalGraphManager {
    
    private final Map<String, TemporalNode> nodes = new HashMap<>();
    private final Map<String, List<TemporalEdge>> edges = new HashMap<>();
    private final SupressionChainTracker suppressionTracker = new SupressionChainTracker();
    
    /**
     * Add a node to the graph
     */
    public TemporalNode addNode(String nodeId, NodeType type, Object data) {
        TemporalNode node = new TemporalNode(nodeId, type, data, LocalDateTime.now());
        nodes.put(nodeId, node);
        log.debug("Added node: {} of type {}", nodeId, type);
        return node;
    }
    
    /**
     * Add a temporal edge with validity window
     */
    public TemporalEdge addEdge(String fromId, String toId, EdgeType type,
                               LocalDateTime validFrom, LocalDateTime validTo,
                               double confidence) {
        TemporalEdge edge = new TemporalEdge(
            fromId, toId, type,
            validFrom, validTo,
            confidence
        );
        
        edges.computeIfAbsent(fromId, k -> new ArrayList<>()).add(edge);
        
        log.debug("Added edge: {} --[{}]--> {} (valid {} to {})",
                 fromId, type, toId, validFrom, validTo);
        
        return edge;
    }
    
    /**
     * Mark a previous edge as superseded
     */
    public void supersede(TemporalEdge oldEdge, TemporalEdge newEdge) {
        // Close old edge validity window at transition point
        oldEdge.validTo = LocalDateTime.now();
        
        // Record supersession relationship
        suppressionTracker.recordSupersession(oldEdge, newEdge);
        
        log.info("Superseded edge {} -> {} with {} -> {}",
                oldEdge.from, oldEdge.to, newEdge.from, newEdge.to);
    }
    
    /**
     * Get edges valid at specific time
     */
    public List<TemporalEdge> getValidEdges(String fromId, LocalDateTime atTime) {
        List<TemporalEdge> allEdges = edges.getOrDefault(fromId, new ArrayList<>());
        
        return allEdges.stream()
            .filter(e -> isValidAtTime(e, atTime))
            .sorted(Comparator.comparingDouble(TemporalEdge::getConfidence).reversed())
            .toList();
    }
    
    /**
     * Traverse graph at specific time point
     */
    public List<String> traverse(String startNodeId, LocalDateTime atTime, 
                                 TraversalType traversalType, int maxDepth) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        traverseRecursive(startNodeId, atTime, traversalType, 0, maxDepth, 
                         result, visited);
        
        return result;
    }
    
    /**
     * Get causal dependencies at a point in time
     */
    public CausalChain getCausalChain(String nodeId, LocalDateTime atTime) {
        CausalChain chain = new CausalChain(nodeId, atTime);
        
        // Find all nodes that causally lead to this node
        Set<String> dependencies = new HashSet<>();
        findCausalDependencies(nodeId, atTime, dependencies);
        
        chain.dependencies = dependencies;
        chain.depth = calculateChainDepth(dependencies);
        
        return chain;
    }
    
    /**
     * Get supersession history for an edge
     */
    public SupressionChain getSuppressionChain(TemporalEdge edge) {
        return suppressionTracker.getChain(edge);
    }
    
    /**
     * Validate graph consistency at time point
     */
    public GraphConsistencyReport validateConsistency(LocalDateTime atTime) {
        GraphConsistencyReport report = new GraphConsistencyReport(atTime);
        
        for (TemporalNode node : nodes.values()) {
            List<TemporalEdge> inEdges = getValidEdges(node.id, atTime);
            
            // Check for conflicting edges
            Map<String, List<TemporalEdge>> byTarget = new HashMap<>();
            inEdges.forEach(e -> 
                byTarget.computeIfAbsent(e.to, k -> new ArrayList<>()).add(e)
            );
            
            for (List<TemporalEdge> conflicts : byTarget.values()) {
                if (conflicts.size() > 1) {
                    double totalConfidence = conflicts.stream()
                        .mapToDouble(TemporalEdge::getConfidence).sum();
                    
                    if (totalConfidence > 1.0) {
                        report.contradictions.add(new Contradiction(node.id, conflicts));
                    }
                }
            }
        }
        
        return report;
    }
    
    // ===== Private helpers =====
    
    private void traverseRecursive(String nodeId, LocalDateTime atTime,
                                  TraversalType type, int depth, int maxDepth,
                                  List<String> result, Set<String> visited) {
        if (depth > maxDepth || visited.contains(nodeId)) {
            return;
        }
        
        visited.add(nodeId);
        result.add(nodeId);
        
        if (type == TraversalType.FORWARD) {
            List<TemporalEdge> outgoing = getValidEdges(nodeId, atTime);
            for (TemporalEdge edge : outgoing) {
                traverseRecursive(edge.to, atTime, type, depth + 1, maxDepth, result, visited);
            }
        } else if (type == TraversalType.BACKWARD) {
            // Find incoming edges
            for (Map.Entry<String, List<TemporalEdge>> entry : edges.entrySet()) {
                for (TemporalEdge edge : entry.getValue()) {
                    if (edge.to.equals(nodeId) && isValidAtTime(edge, atTime)) {
                        traverseRecursive(edge.from, atTime, type, depth + 1, maxDepth, result, visited);
                    }
                }
            }
        }
    }
    
    private void findCausalDependencies(String nodeId, LocalDateTime atTime, Set<String> deps) {
        List<TemporalEdge> causes = edges.getOrDefault(nodeId, new ArrayList<>()).stream()
            .filter(e -> e.type == EdgeType.CAUSES && isValidAtTime(e, atTime))
            .toList();
        
        for (TemporalEdge edge : causes) {
            if (deps.add(edge.from)) {
                findCausalDependencies(edge.from, atTime, deps);
            }
        }
    }
    
    private int calculateChainDepth(Set<String> nodes) {
        // Simple heuristic: log scale
        return (int) Math.ceil(Math.log(nodes.size() + 1));
    }
    
    private boolean isValidAtTime(TemporalEdge edge, LocalDateTime atTime) {
        return !atTime.isBefore(edge.validFrom) && !atTime.isAfter(edge.validTo);
    }
    
    // ===== Data Models =====
    
    @Data
    public static class TemporalNode {
        public String id;
        public NodeType type;
        public Object data;
        public LocalDateTime created;
        
        public TemporalNode(String id, NodeType type, Object data, LocalDateTime created) {
            this.id = id;
            this.type = type;
            this.data = data;
            this.created = created;
        }
    }
    
    @Data
    public static class TemporalEdge {
        public String from;
        public String to;
        public EdgeType type;
        public LocalDateTime validFrom;
        public LocalDateTime validTo;
        public double confidence;
        
        public TemporalEdge(String from, String to, EdgeType type,
                           LocalDateTime validFrom, LocalDateTime validTo,
                           double confidence) {
            this.from = from;
            this.to = to;
            this.type = type;
            this.validFrom = validFrom;
            this.validTo = validTo;
            this.confidence = confidence;
        }
        
        public double getConfidence() {
            return confidence;
        }
    }
    
    @Data
    public static class CausalChain {
        public String nodeId;
        public LocalDateTime atTime;
        public Set<String> dependencies;
        public int depth;
    }
    
    @Data
    public static class SupressionChain {
        public List<TemporalEdge> history;
        public TemporalEdge current;
    }
    
    @Data
    public static class GraphConsistencyReport {
        public LocalDateTime atTime;
        public List<Contradiction> contradictions = new ArrayList<>();
        
        public boolean isConsistent() {
            return contradictions.isEmpty();
        }
    }
    
    @Data
    public static class Contradiction {
        public String nodeId;
        public List<TemporalEdge> conflictingEdges;
        
        public Contradiction(String id, List<TemporalEdge> edges) {
            this.nodeId = id;
            this.conflictingEdges = edges;
        }
    }
    
    public enum NodeType {
        ENTITY, MEMORY, EVENT, PROJECT, FACT
    }
    
    public enum EdgeType {
        CAUSES,
        UPDATES,
        SUPERSEDES,
        RELATES_TO,
        DEPENDS_ON
    }
    
    public enum TraversalType {
        FORWARD,
        BACKWARD,
        BIDIRECTIONAL
    }
    
    // ===== Internals =====
    
    private static class SupressionChainTracker {
        private final Map<TemporalEdge, List<TemporalEdge>> chainMap = new HashMap<>();
        
        void recordSupersession(TemporalEdge old, TemporalEdge newEdge) {
            chainMap.computeIfAbsent(newEdge, k -> new ArrayList<>()).add(old);
        }
        
        SupressionChain getChain(TemporalEdge edge) {
            SupressionChain chain = new SupressionChain();
            chain.current = edge;
            chain.history = chainMap.getOrDefault(edge, new ArrayList<>());
            return chain;
        }
    }
}
