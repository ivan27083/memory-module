package com.openclaw.memory.retrieval;

import java.util.List;

// ✅ Переименован из RetrievalExplanation → RetrieverExplanation
public record RetrieverExplanation(
    String method,
    List<String> path,
    double bm25Score,
    double vectorScore,
    double graphScore,
    String rrfDetails
) {}