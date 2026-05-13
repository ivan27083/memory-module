package com.openclaw.memory.domain.model;

import java.util.Map;

public record MemoryWriteCommand(
        String agentId,
        String sessionId,
        MemoryType type,
        String content,
        Map<String, Object> metadata
) {
    public MemoryWriteCommand {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
