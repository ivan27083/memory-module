package com.openclaw.memory.domain.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record VectorDocument(
        UUID id,
        MemoryType sourceType,
        String content,
        List<Double> vector,
        Map<String, Object> metadata
) {
    public VectorDocument {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        vector = vector == null ? List.of() : List.copyOf(vector);
    }
}
