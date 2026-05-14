package com.openclaw.memory.agents.retrieval;

import com.openclaw.memory.agents.BaseAgent;
import com.openclaw.memory.blackboard.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Retrieval Agent Interface (QMD - Query Multi-Dimensional)
 * 
 * Ответственность:
 * - Реализует гибридный конвейер извлечения
 * - Управляет BM25 + vector + rerank fusion
 * - Владеет декомпозицией запроса (lex/vec/hyde/expand)
 */
public interface RetrievalAgent extends BaseAgent {
    
    /**
     * Декомпозировать запрос на подзапросы
     */
    List<Query> decomposeQuery(String query);
    
    /**
     * Выполнить BM25 поиск
     */
    List<RetrievalResult> bm25Search(String query, int topK);
    
    /**
     * Выполнить vector поиск
     */
    List<RetrievalResult> vectorSearch(String query, int topK);
    
    /**
     * Выполнить переранжирование результатов
     */
    List<RetrievalResult> rerank(List<RetrievalResult> results, String query, int topK);
    
    /**
     * Гибридный поиск (BM25 + vector + rerank)
     */
    CompletableFuture<List<RetrievalResult>> hybridSearch(String query, int topK);
    
    /**
     * Получить объяснение результатов поиска
     */
    RetrievalExplanation explainResults(List<RetrievalResult> results, String query);
    
    /**
     * Получить статистику поиска
     */
    RetrievalStats getRetrievalStats();
    
    class Query {
        public final String originalQuery;
        public final String decomposedQuery;
        public final QueryType type;
        
        public enum QueryType {
            LEXICAL,
            SEMANTIC,
            HYBRID,
            EXPANSION,
            HYDE // Hypothetical Document Embeddings
        }
        
        public Query(String original, String decomposed, QueryType type) {
            this.originalQuery = original;
            this.decomposedQuery = decomposed;
            this.type = type;
        }
    }
    
    class RetrievalResult {
        public final String documentId;
        public final String content;
        public final float relevanceScore;
        public final float bm25Score;
        public final float vectorScore;
        public final float rerankScore;
        public final String source;
        
        public RetrievalResult(String docId, String content, float relevance,
                             float bm25, float vector, float rerank, String source) {
            this.documentId = docId;
            this.content = content;
            this.relevanceScore = relevance;
            this.bm25Score = bm25;
            this.vectorScore = vector;
            this.rerankScore = rerank;
            this.source = source;
        }
    }
    
    class RetrievalExplanation {
        public final List<String> queriesUsed;
        public final List<String> indicesSearched;
        public final String fusionStrategy;
        public final long totalLatencyMs;
        public final String explanation;
        
        public RetrievalExplanation(List<String> queries, List<String> indices,
                                   String fusion, long latency, String explanation) {
            this.queriesUsed = queries;
            this.indicesSearched = indices;
            this.fusionStrategy = fusion;
            this.totalLatencyMs = latency;
            this.explanation = explanation;
        }
    }
    
    class RetrievalStats {
        public final long queriesProcessed;
        public final long documentsRetrieved;
        public final double averageLatencyMs;
        public final double averagePrecision;
        public final double cacheHitRate;
        
        public RetrievalStats(long queries, long documents, double latency,
                            double precision, double cacheHit) {
            this.queriesProcessed = queries;
            this.documentsRetrieved = documents;
            this.averageLatencyMs = latency;
            this.averagePrecision = precision;
            this.cacheHitRate = cacheHit;
        }
    }
}
