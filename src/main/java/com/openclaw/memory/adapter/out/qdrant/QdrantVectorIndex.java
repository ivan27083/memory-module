package com.openclaw.memory.adapter.out.qdrant;

import com.openclaw.memory.config.MemoryModuleProperties;
import com.openclaw.memory.domain.model.MemoryType;
import com.openclaw.memory.domain.model.RetrievalResult;
import com.openclaw.memory.domain.model.VectorDocument;
import com.openclaw.memory.domain.port.VectorIndex;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class QdrantVectorIndex implements VectorIndex {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorIndex.class);

    private final RestClient restClient;
    private final MemoryModuleProperties properties;

    public QdrantVectorIndex(RestClient.Builder restClientBuilder, MemoryModuleProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.vector().qdrantUrl())
                .build();
        this.properties = properties;
    }

    @PostConstruct
    @Override
    public void ensureCollection() {
        try {
            restClient.put()
                    .uri("/collections/{collection}", properties.vector().collection())
                    .body(Map.of("vectors", Map.of(
                            "size", properties.vector().dimensions(),
                            "distance", "Cosine"
                    )))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict ignored) {
            // Existing collection is expected after the first successful startup.
        }
    }

    @Override
    public void upsert(VectorDocument document) {
        int expectedDimensions = properties.vector().dimensions();
        int actualDimensions = document.vector().size();
        if (actualDimensions != expectedDimensions) {
            throw new IllegalArgumentException(
                    "Vector dimensions mismatch: expected " + expectedDimensions + " but embedding provider returned " + actualDimensions
            );
        }

        Map<String, Object> payload = new LinkedHashMap<>(document.metadata());
        payload.put("content", document.content());
        payload.put("sourceType", document.sourceType().name());

        log.debug("Upserting vector document id={}, sourceType={}, dimensions={}",
                document.id(), document.sourceType(), actualDimensions);
        restClient.put()
                .uri("/collections/{collection}/points?wait=true", properties.vector().collection())
                .body(Map.of("points", List.of(Map.of(
                        "id", document.id().toString(),
                        "vector", document.vector(),
                        "payload", payload
                ))))
                .retrieve()
                .toBodilessEntity();
        log.debug("Vector document upserted id={}", document.id());
    }

    @Override
    public List<RetrievalResult> search(List<Double> vector, int limit, Map<String, Object> filters) {
        Map<?, ?> response = restClient.post()
                .uri("/collections/{collection}/points/search", properties.vector().collection())
                .body(Map.of(
                        "vector", vector,
                        "limit", limit,
                        "with_payload", true,
                        "filter", filter(filters)
                ))
                .retrieve()
                .body(Map.class);

        if (response == null || !(response.get("result") instanceof List<?> result)) {
            return List.of();
        }
        return result.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(this::toResult)
                .toList();
    }

    private RetrievalResult toResult(Map<?, ?> point) {
        Map<?, ?> payload = point.get("payload") instanceof Map<?, ?> map ? map : Map.of();
        Object contentRaw = payload.get("content");
        Object sourceTypeRaw = payload.get("sourceType");
        String content = contentRaw == null ? "" : String.valueOf(contentRaw);
        MemoryType sourceType = parseType(sourceTypeRaw == null ? MemoryType.VECTOR.name() : String.valueOf(sourceTypeRaw));
        double score = point.get("score") instanceof Number number ? number.doubleValue() : 0.0d;

        Map<String, Object> metadata = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : payload.entrySet()) {
            metadata.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        metadata.remove("content");

        return new RetrievalResult(idFrom(point.get("id")), sourceType, content, score, metadata, Instant.now());
    }

    private static UUID idFrom(Object raw) {
        String value = String.valueOf(raw);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static MemoryType parseType(String value) {
        try {
            return MemoryType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return MemoryType.VECTOR;
        }
    }

    private static Map<String, Object> filter(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> must = filters.entrySet().stream()
                .map(entry -> Map.<String, Object>of(
                        "key", entry.getKey(),
                        "match", Map.of("value", entry.getValue())
                ))
                .toList();
        return Map.of("must", must);
    }
}
