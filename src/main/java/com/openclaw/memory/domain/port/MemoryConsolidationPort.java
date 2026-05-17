package com.openclaw.memory.domain.port;

import com.openclaw.memory.domain.model.MemoryRecord;

public interface MemoryConsolidationPort {
    void indexMemory(MemoryRecord record);
}
