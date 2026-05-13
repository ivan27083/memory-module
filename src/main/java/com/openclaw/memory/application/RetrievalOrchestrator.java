package com.openclaw.memory.application;

import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.model.MemoryType;
import com.openclaw.memory.domain.model.RetrievalQuery;
import com.openclaw.memory.domain.model.RetrievalResult;
import com.openclaw.memory.domain.port.EmbeddingClient;
import com.openclaw.memory.domain.port.EpisodicMemoryRepository;
import com.openclaw.memory.domain.port.ExternalKnowledgeRetriever;
import com.openclaw.memory.domain.port.Reranker;
import com.openclaw.memory.domain.port.SemanticWikiRepository;
import com.openclaw.memory.domain.port.VectorIndex;
import com.openclaw.memory.domain.port.WorkingMemoryStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RetrievalOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RetrievalOrchestrator.class);

    private final WorkingMemoryStore workingMemoryStore;
    private final EpisodicMemoryRepository episodicMemoryRepository;
    private final SemanticWikiRepository semanticWikiRepository;
    private final VectorIndex vectorIndex;
    private final ExternalKnowledgeRetriever externalKnowledgeRetriever;
    private final EmbeddingClient embeddingClient;
    private final Reranker reranker;

    public RetrievalOrchestrator(
            WorkingMemoryStore workingMemoryStore,
            EpisodicMemoryRepository episodicMemoryRepository,
            SemanticWikiRepository semanticWikiRepository,
            VectorIndex vectorIndex,
            ExternalKnowledgeRetriever externalKnowledgeRetriever,
            EmbeddingClient embeddingClient,
            Reranker reranker
    ) {
        this.workingMemoryStore = workingMemoryStore;
        this.episodicMemoryRepository = episodicMemoryRepository;
        this.semanticWikiRepository = semanticWikiRepository;
        this.vectorIndex = vectorIndex;
        this.externalKnowledgeRetriever = externalKnowledgeRetriever;
        this.embeddingClient = embeddingClient;
        this.reranker = reranker;
    }

    public List<RetrievalResult> retrieve(RetrievalQuery query) {
        validate(query);

        int finalLimit = Math.max(query.limit(), 1);
        int sourceLimit = finalLimit;
        List<RetrievalResult> candidates = new ArrayList<>();
        addSource("working memory", candidates, () ->
                asResults(workingMemoryStore.findRecent(query.agentId(), query.sessionId(), sourceLimit), 0.95));
        addSource("episodic memory", candidates, () ->
                asResults(episodicMemoryRepository.findRecent(query.agentId(), query.sessionId(), sourceLimit), 0.75));
        addSource("semantic wiki memory", candidates, () ->
                asResults(semanticWikiRepository.findRelevant(query.agentId(), query.prompt(), sourceLimit), 0.80));
        addSource("vector memory", candidates, () ->
                vectorIndex.search(embeddingClient.embed(query.prompt()), sourceLimit, Map.of("agentId", query.agentId())));
        addSource("external RAG", candidates, () ->
                externalKnowledgeRetriever.retrieve(query));

        return reranker.rerank(query, candidates).stream()
                .limit(finalLimit)
                .toList();
    }

    private static void addSource(String sourceName, List<RetrievalResult> candidates, RetrievalSource source) {
        try {
            candidates.addAll(source.retrieve());
        } catch (RuntimeException ex) {
            log.warn("Skipping {} retrieval source after failure", sourceName, ex);
        }
    }

    private static List<RetrievalResult> asResults(List<MemoryRecord> records, double score) {
        return records.stream()
                .map(record -> new RetrievalResult(
                        record.id(),
                        record.type(),
                        record.content(),
                        score,
                        record.metadata(),
                        record.createdAt()
                ))
                .sorted(Comparator.comparing(RetrievalResult::createdAt).reversed())
                .toList();
    }

    private static void validate(RetrievalQuery query) {
        if (!StringUtils.hasText(query.agentId())) {
            throw new IllegalArgumentException("agentId is required");
        }
        if (!StringUtils.hasText(query.prompt())) {
            throw new IllegalArgumentException("prompt is required");
        }
    }

    @FunctionalInterface
    private interface RetrievalSource {
        List<RetrievalResult> retrieve();
    }
}
