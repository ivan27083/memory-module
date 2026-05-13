package com.openclaw.memory.adapter.out.ai;

import com.openclaw.memory.config.MemoryModuleProperties;
import com.openclaw.memory.domain.port.EmbeddingClient;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    private final RestClient restClient;
    private final MemoryModuleProperties properties;

    public OpenAiCompatibleEmbeddingClient(RestClient.Builder restClientBuilder, MemoryModuleProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.embedding().baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.embedding().apiKey())
                .build();
        this.properties = properties;
    }

    @Override
    public List<Double> embed(String content) {
        Map<?, ?> response = restClient.post()
                .uri("/embeddings")
                .body(Map.of("model", properties.embedding().model(), "input", content))
                .retrieve()
                .body(Map.class);

        if (response == null || !(response.get("data") instanceof List<?> data) || data.isEmpty()) {
            throw new IllegalStateException("Embedding provider returned no embedding data");
        }
        if (!(data.getFirst() instanceof Map<?, ?> first) || !(first.get("embedding") instanceof List<?> embedding)) {
            throw new IllegalStateException("Embedding provider response has unexpected shape");
        }
        return embedding.stream()
                .map(Number.class::cast)
                .map(Number::doubleValue)
                .toList();
    }
}
