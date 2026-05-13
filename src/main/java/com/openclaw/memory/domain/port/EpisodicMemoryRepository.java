package com.openclaw.memory.domain.port;

import com.openclaw.memory.domain.model.MemoryRecord;
import java.util.List;

public interface EpisodicMemoryRepository {
    MemoryRecord save(MemoryRecord record);

    List<MemoryRecord> findRecent(String agentId, String sessionId, int limit);
}
