package com.openclaw.memory.domain.model;

import java.util.Map;
import java.util.UUID;

public record DocumentChunk(
        UUID id,
        String source,
        String title,
        int ordinal,
        String content,
        Map<String, Object> metadata
) {
    public DocumentChunk {
        id = id == null ? UUID.randomUUID() : id;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
