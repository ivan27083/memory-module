package com.openclaw.memory.domain.model;

import java.util.Map;

public record RetrievalQuery(
        String agentId,
        String sessionId,
        String prompt,
        int limit,
        Map<String, Object> metadata
) {
    public RetrievalQuery {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
