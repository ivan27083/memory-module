package com.openclaw.memory.retrieval;

import java.util.Map;

import com.openclaw.memory.blackboard.Artifact;

public record RetrievalResult(
    String memoryId,
    String content,
    double score,
    Map<String, Object> metadata,
    RetrieverExplanation explanation,   // ✅ было RetrievalExplanation
    Artifact artifact   
) {
    public static RetrievalResult of(String memoryId, String content, double score) {
        return new RetrievalResult(memoryId, content, score, Map.of(), null, null);
    }
}