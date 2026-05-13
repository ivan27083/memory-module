package com.openclaw.memory.domain.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record MemoryRecord(
        UUID id,
        String agentId,
        String sessionId,
        MemoryType type,
        String content,
        Map<String, Object> metadata,
        Instant createdAt
) {
    public MemoryRecord {
        id = id == null ? UUID.randomUUID() : id;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public MemoryRecord withMetadata(String key, Object value) {
        Map<String, Object> next = new LinkedHashMap<>(metadata);
        next.put(key, value);
        return new MemoryRecord(id, agentId, sessionId, type, content, next, createdAt);
    }
}
