package com.openclaw.memory.domain.port;

import com.openclaw.memory.domain.model.RetrievalResult;
import com.openclaw.memory.domain.model.VectorDocument;
import java.util.List;
import java.util.Map;

public interface VectorIndex {
    void ensureCollection();

    void upsert(VectorDocument document);

    void delete(java.util.UUID id);

    List<RetrievalResult> search(List<Double> vector, int limit, Map<String, Object> filters);
}
