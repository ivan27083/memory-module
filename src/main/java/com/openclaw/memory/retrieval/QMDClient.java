package com.openclaw.memory.retrieval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.memory.config.QMDClientProperties;
import com.openclaw.memory.domain.model.MemoryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for the qmd-server Node.js sidecar.
 * Instantiated only when memory.qmd.enabled=true.
 */
public class QMDClient {

    private static final Logger log = LoggerFactory.getLogger(QMDClient.class);

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final java.time.Duration requestTimeout;
    private final boolean rerank;
    private final double minScore;

    public record SearchResult(String id, String content, double score, Map<String, Object> metadata) {}

    public QMDClient(QMDClientProperties props, ObjectMapper mapper) {
        this.baseUrl        = props.getBaseUrl();
        this.requestTimeout = props.getRequestTimeout();
        this.rerank         = props.isRerank();
        this.minScore       = props.getMinScore();
        this.mapper         = mapper;

        this.http = HttpClient.newBuilder()
                .connectTimeout(props.getConnectTimeout())
                .build();
    }

    // ── Index ─────────────────────────────────────────────────────────────────

    public void index(MemoryRecord record) {
        try {
            Map<String, Object> body = Map.of(
                "id",        record.id().toString(),
                "agentId",   record.agentId(),
                "sessionId", record.sessionId() != null ? record.sessionId() : "",
                "type",      record.type().name(),
                "content",   record.content(),
                "metadata",  record.metadata(),
                "createdAt", record.createdAt().toString()
            );

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/index"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .timeout(requestTimeout)
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("QMD index returned {}: {}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.warn("QMD index failed for record {}: {}", record.id(), e.getMessage());
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    public List<SearchResult> search(String query, int topK) {
        try {
            Map<String, Object> body = Map.of(
                "query",    query,
                "limit",    topK,
                "rerank",   rerank,
                "minScore", minScore
            );

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/search"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .timeout(requestTimeout)
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("QMD search returned {}: {}", resp.statusCode(), resp.body());
                return List.of();
            }

            Map<String, Object> parsed = mapper.readValue(resp.body(), new TypeReference<>() {});
            List<?> raw = (List<?>) parsed.getOrDefault("results", List.of());

            return raw.stream()
                    .map(item -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = (Map<String, Object>) item;
                        return new SearchResult(
                                String.valueOf(m.getOrDefault("id", "")),
                                String.valueOf(m.getOrDefault("content", "")),
                                toDouble(m.getOrDefault("score", 0.5)),
                                getMetadata(m)
                        );
                    })
                    .toList();

        } catch (Exception e) {
            log.warn("QMD search failed for query '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    // ── Health ────────────────────────────────────────────────────────────────

    public boolean isHealthy() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/health"))
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(2))
                    .build();
            return http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return 0.5; }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMetadata(Map<String, Object> m) {
        Object meta = m.get("metadata");
        return (meta instanceof Map<?, ?>) ? (Map<String, Object>) meta : Map.of();
    }
}
