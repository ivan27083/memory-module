package com.openclaw.memory.domain.port;

import com.openclaw.memory.domain.model.MemoryRecord;
import java.time.Duration;
import java.util.List;

public interface WorkingMemoryStore {
    MemoryRecord save(MemoryRecord record, Duration ttl);

    List<MemoryRecord> findRecent(String agentId, String sessionId, int limit);
}
