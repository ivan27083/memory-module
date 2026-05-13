package com.openclaw.memory.application;

import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.model.MemoryWriteCommand;
import com.openclaw.memory.domain.model.RetrievalQuery;
import com.openclaw.memory.domain.model.RetrievalResult;
import java.util.List;

public interface MemoryFacade {
    MemoryRecord write(MemoryWriteCommand command);

    List<RetrievalResult> retrieve(RetrievalQuery query);
}
