package com.openclaw.memory.domain.port;

import com.openclaw.memory.domain.model.MemoryRecord;
import java.util.List;

public interface SemanticWikiRepository {
    MemoryRecord upsert(String title, MemoryRecord record);

    List<MemoryRecord> findRelevant(String agentId, String query, int limit);
}
