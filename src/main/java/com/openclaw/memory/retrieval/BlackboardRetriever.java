package com.openclaw.memory.retrieval;

import com.openclaw.memory.blackboard.Artifact;
import com.openclaw.memory.blackboard.MemoryBlackboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class BlackboardRetriever implements Retriever {

    private static final Logger log = LoggerFactory.getLogger(BlackboardRetriever.class);

    private final MemoryBlackboard blackboard;

    public BlackboardRetriever(MemoryBlackboard blackboard) {
        this.blackboard = blackboard;
    }

    @Override
    public CompletableFuture<List<RetrievalResult>> search(String query, int topK) {
        // Blackboard — синхронный in-memory store, async не нужен
        return CompletableFuture.completedFuture(doSearch(query, topK));
    }

    private List<RetrievalResult> doSearch(String query, int topK) {
        log.debug("BlackboardRetriever.search: query='{}', topK={}", query, topK);

        String lowerQuery = query.toLowerCase();

        // getAllArtifacts() не существует — итерируем по всем ArtifactType
        return Arrays.stream(Artifact.ArtifactType.values())
                .flatMap(type -> blackboard.getArtifactsByType(type).stream())
                .filter(a -> a.getContent() != null
                          && a.getContent().toLowerCase().contains(lowerQuery))
                .limit(topK)
                .map(a -> toResult(a, lowerQuery))
                .collect(Collectors.toList());
    }

    private RetrievalResult toResult(Artifact artifact, String query) {
        double score = Math.min(
            (double) query.length() / Math.max(artifact.getContent().length(), 1),
            1.0
        );

        Map<String, Object> meta = new HashMap<>();
        meta.put("type",      artifact.getType().name());
        meta.put("timestamp", artifact.getTimestamp().toString());
        meta.put("source",    artifact.getProducedBy());

        RetrieverExplanation explanation = new RetrieverExplanation(
            "legacy",
            List.of("substring_match"),
            0.0, 0.0, 0.0,
            "provider=legacy"
        );

        return new RetrievalResult(
            artifact.getArtifactId(),
            artifact.getContent(),
            score,
            meta,
            explanation, 
            artifact
        );
    }
}