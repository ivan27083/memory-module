package com.openclaw.memory.retrieval;

import com.openclaw.memory.blackboard.Artifact;
import com.openclaw.memory.blackboard.Provenance;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * QMD Retrieval Engine - Query Morphology Decomposition.
 * 
 * Implements hybrid retrieval pipeline:
 * 1. Query Decomposition (lex, vec, hyde, expand)
 * 2. Candidate Retrieval (BM25, vector, graph)
 * 3. Fusion (RRF ranking)
 * 4. Reranking (cross-encoder)
 * 5. Explanation output
 */
@Slf4j
public class QMDRetrievalEngine {
    
    private final BM25Retriever bm25Retriever;
    private final VectorRetriever vectorRetriever;
    private final GraphRetriever graphRetriever;
    private final CrossEncoderReranker reranker;
    private final QueryDecomposer queryDecomposer;
    
    public QMDRetrievalEngine() {
        this((queries, topK) -> List.of(), (queries, topK) -> List.of(), (queries, topK) -> List.of(),
             (candidates, query, topN) -> candidates.stream().limit(topN).collect(Collectors.toList()),
             query -> {
                 QueryMorphology morphology = new QueryMorphology();
                 morphology.originalQuery = query;
                 morphology.lexicalQueries = List.of(query);
                 morphology.vectorQueries = List.of(query);
                 morphology.expandedQueries = List.of(query);
                 morphology.hypotheses = List.of();
                 return morphology;
             });
    }

    public QMDRetrievalEngine(BM25Retriever bm25, VectorRetriever vector, 
                            GraphRetriever graph, CrossEncoderReranker reranker,
                            QueryDecomposer decomposer) {
        this.bm25Retriever = bm25;
        this.vectorRetriever = vector;
        this.graphRetriever = graph;
        this.reranker = reranker;
        this.queryDecomposer = decomposer;
    }
    
    /**
     * Main retrieval pipeline
     */
    public RetrievalResults retrieve(String query, RetrievalOptions options) {
        long startTime = System.currentTimeMillis();
        
        // 1. Query Decomposition
        QueryMorphology morphology = queryDecomposer.decompose(query);
        log.info("Query morphology: {}", morphology);
        
        // 2. Candidate Retrieval (parallel)
        List<RankedCandidate> bm25Results = bm25Retriever.retrieve(morphology.lexicalQueries, options.topK);
        List<RankedCandidate> vectorResults = vectorRetriever.retrieve(morphology.vectorQueries, options.topK);
        List<RankedCandidate> graphResults = graphRetriever.retrieve(morphology.expandedQueries, options.topK);
        
        // 3. Fusion (Reciprocal Rank Fusion)
        List<RankedCandidate> fused = rrfFusion(
            bm25Results, vectorResults, graphResults,
            options.rrfK
        );
        
        // 4. Reranking
        List<RankedCandidate> reranked = reranker.rerank(fused, query, options.topN);
        
        // 5. Build results with explanations
        long elapsed = System.currentTimeMillis() - startTime;
        
        return new RetrievalResults(
            reranked,
            new RetrievalExplanation(morphology, fused, reranked, elapsed),
            elapsed
        );
    }
    
    /**
     * Compatibility search facade used by MCP adapters and tests.
     */
    public List<com.openclaw.memory.domain.model.RetrievalResult> search(String query, int topK, double confidenceThreshold) {
        RetrievalOptions options = new RetrievalOptions();
        options.topK = topK;
        options.topN = topK;
        return retrieve(query, options).results.stream()
            .filter(candidate -> candidate.finalScore >= confidenceThreshold || candidate.getScore() >= confidenceThreshold)
            .limit(topK)
            .map(candidate -> new com.openclaw.memory.domain.model.RetrievalResult(
                    toUuid(candidate.artifactId),
                    com.openclaw.memory.domain.model.MemoryType.VECTOR,
                    candidate.artifact == null ? "" : candidate.artifact.getContent(),
                    candidate.finalScore > 0 ? candidate.finalScore : candidate.getScore(),
                    Map.of("artifactId", candidate.artifactId),
                    java.time.Instant.now()))
            .collect(Collectors.toList());
    }

    private static java.util.UUID toUuid(String value) {
        try {
            return java.util.UUID.fromString(value);
        } catch (Exception ignored) {
            return java.util.UUID.nameUUIDFromBytes(String.valueOf(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /**
     * Reciprocal Rank Fusion - combines multiple ranker outputs
     */
    private List<RankedCandidate> rrfFusion(List<RankedCandidate> bm25, 
                                           List<RankedCandidate> vector,
                                           List<RankedCandidate> graph,
                                           double rrfK) {
        Map<String, Double> scores = new HashMap<>();
        
        // Accumulate RRF scores: 1/(k + rank)
        applyRRF(bm25, scores, rrfK);
        applyRRF(vector, scores, rrfK);
        applyRRF(graph, scores, rrfK);
        
        return scores.entrySet().stream()
            .map(e -> {
                // Find the best candidate representation
                RankedCandidate best = Stream.of(bm25, vector, graph)
                    .flatMap(List::stream)
                    .filter(c -> c.artifactId.equals(e.getKey()))
                    .max(Comparator.comparingDouble(RankedCandidate::getScore))
                    .orElseThrow();
                
                best.rrfScore = e.getValue();
                return best;
            })
            .sorted(Comparator.comparingDouble(RankedCandidate::getRrfScore).reversed())
            .collect(Collectors.toList());
    }
    
    private void applyRRF(List<RankedCandidate> results, Map<String, Double> scores, double k) {
        for (int i = 0; i < results.size(); i++) {
            RankedCandidate candidate = results.get(i);
            double rrfScore = 1.0 / (k + (i + 1));
            scores.merge(candidate.artifactId, rrfScore, Double::sum);
        }
    }
    
    // ===== Data Models =====
    
    @Data
    public static class QueryMorphology {
        private String originalQuery;
        private List<String> lexicalQueries;      // BM25
        private List<String> vectorQueries;       // Semantic
        private List<String> expandedQueries;     // Graph
        private List<String> hypotheses;          // HyDE
    }
    
    @Data
    public static class RankedCandidate {
        public String artifactId;
        public Artifact artifact;
        public double bm25Score;
        public double vectorScore;
        public double graphScore;
        public double rrfScore;
        public double finalScore;
        
        public double getScore() {
            return Math.max(Math.max(bm25Score, vectorScore), graphScore);
        }
        
        public double getRrfScore() {
            return rrfScore;
        }
    }
    
    @Data
    public static class RetrievalOptions {
        public int topK = 100;           // Candidate pool
        public int topN = 10;            // Final results
        public double rrfK = 60;         // RRF parameter
        public boolean includeExplanation = true;
    }
    
    @Data
    public static class RetrievalResults {
        public List<RankedCandidate> results;
        public RetrievalExplanation explanation;
        public long elapsedMs;

        public RetrievalResults(List<RankedCandidate> results, RetrievalExplanation explanation, long elapsedMs) {
            this.results = results;
            this.explanation = explanation;
            this.elapsedMs = elapsedMs;
        }
    }
    
    @Data
    public static class RetrievalExplanation {
        public QueryMorphology queryMorphology;
        public List<RankedCandidate> fusedResults;
        public List<RankedCandidate> finalResults;
        public long totalTimeMs;

        public RetrievalExplanation(QueryMorphology queryMorphology, List<RankedCandidate> fusedResults,
                                    List<RankedCandidate> finalResults, long totalTimeMs) {
            this.queryMorphology = queryMorphology;
            this.fusedResults = fusedResults;
            this.finalResults = finalResults;
            this.totalTimeMs = totalTimeMs;
        }
    }
    
    // ===== Subcomponents (interfaces) =====
    
    public interface BM25Retriever {
        List<RankedCandidate> retrieve(List<String> queries, int topK);
    }
    
    public interface VectorRetriever {
        List<RankedCandidate> retrieve(List<String> queries, int topK);
    }
    
    public interface GraphRetriever {
        List<RankedCandidate> retrieve(List<String> queries, int topK);
    }
    
    public interface CrossEncoderReranker {
        List<RankedCandidate> rerank(List<RankedCandidate> candidates, String query, int topN);
    }
    
    public interface QueryDecomposer {
        QueryMorphology decompose(String query);
    }
}
