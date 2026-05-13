package com.openclaw.memory.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RetrievalResult(
        UUID sourceId,
        MemoryType sourceType,
        String content,
        double score,
        Map<String, Object> metadata,
        Instant createdAt
) {
    public RetrievalResult {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
