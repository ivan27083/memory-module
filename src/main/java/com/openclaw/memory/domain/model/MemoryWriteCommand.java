package com.openclaw.memory.domain.model;

import java.util.HashMap;
import java.util.Map;

public record MemoryWriteCommand(
        String agentId,
        String sessionId,
        MemoryType type,
        String content,
        Map<String, Object> metadata
) {
    public MemoryWriteCommand {
        if (metadata == null) {
            metadata = Map.of();
        } else {
            Map<String, Object> copy = new HashMap<>(metadata);
            copy.values().removeIf(v -> v == null);
            metadata = Map.copyOf(copy);
        }
    }
}
