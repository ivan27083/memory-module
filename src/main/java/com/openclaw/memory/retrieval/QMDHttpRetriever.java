package com.openclaw.memory.retrieval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Retriever implementation backed by the qmd-server Node.js sidecar.
 * Falls back to an empty result list when the sidecar is unreachable
 * so the rest of the retrieval pipeline (working/episodic/vector) still works.
 */
public class QMDHttpRetriever implements Retriever {

    private static final Logger log = LoggerFactory.getLogger(QMDHttpRetriever.class);

    private final QMDClient client;

    public QMDHttpRetriever(QMDClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<List<RetrievalResult>> search(String query, int topK) {
        return CompletableFuture.supplyAsync(() -> {
            List<QMDClient.SearchResult> hits = client.search(query, topK);

            if (hits.isEmpty()) {
                log.debug("QMD returned no results for query='{}'", query);
                return List.of();
            }

            return hits.stream()
                    .map(h -> new RetrievalResult(
                            h.id(),
                            h.content(),
                            h.score(),
                            enrichMetadata(h.metadata()),
                            new RetrieverExplanation(
                                    "qmd-http",
                                    List.of("bm25", "vector", "rrf_fusion"),
                                    0.0, 0.0, 0.0,
                                    "provider=qmd-http"
                            ),
                            null
                    ))
                    .toList();
        });
    }

    private static Map<String, Object> enrichMetadata(Map<String, Object> raw) {
        if (raw.containsKey("source")) return raw;
        var m = new java.util.LinkedHashMap<>(raw);
        m.put("source", "qmd-http");
        return m;
    }
}
