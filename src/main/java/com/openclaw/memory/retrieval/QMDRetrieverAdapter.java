package com.openclaw.memory.retrieval;

import com.openclaw.memory.blackboard.Artifact;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class QMDRetrieverAdapter implements Retriever {

    private final QMDRetrievalEngine engine;

    public QMDRetrieverAdapter(QMDRetrievalEngine engine) {
        this.engine = engine;
    }

    @Override
    public CompletableFuture<List<RetrievalResult>> search(String query, int topK) {
        return CompletableFuture.supplyAsync(() -> {
            QMDRetrievalEngine.RetrievalOptions opts = new QMDRetrievalEngine.RetrievalOptions();
            opts.topK = topK * 10;
            opts.topN = topK;

            QMDRetrievalEngine.RetrievalResults qmdResults = engine.retrieve(query, opts);

            return qmdResults.results.stream()
                    .map(this::toRetrievalResult)
                    .toList();
        });
    }

    private RetrievalResult toRetrievalResult(QMDRetrievalEngine.RankedCandidate candidate) {
        Artifact artifact = candidate.artifact;

        RetrieverExplanation explanation = new RetrieverExplanation(
                "qmd",
                List.of("lexical", "vector", "graph", "rrf_fusion", "rerank"),
                candidate.bm25Score,
                candidate.vectorScore,
                candidate.graphScore,
                "rrf_score=" + candidate.rrfScore   // ✅ было rrfRank
        );

        Map<String, Object> metadata = artifact != null
                ? Map.of(
                    "type",      artifact.getType().name(),
                    "timestamp", artifact.getTimestamp().toString()  // ✅ было getValidFrom/getValidTo
                  )
                : Map.of();

        return new RetrievalResult(
                candidate.artifactId,
                artifact != null ? artifact.getContent() : "",
                candidate.finalScore > 0 ? candidate.finalScore : candidate.getScore(),
                metadata,
                explanation,
                artifact
        );
    }
}