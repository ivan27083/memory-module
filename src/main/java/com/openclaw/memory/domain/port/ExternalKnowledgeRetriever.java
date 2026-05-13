package com.openclaw.memory.domain.port;

import com.openclaw.memory.domain.model.RetrievalQuery;
import com.openclaw.memory.domain.model.RetrievalResult;
import java.util.List;

public interface ExternalKnowledgeRetriever {
    List<RetrievalResult> retrieve(RetrievalQuery query);
}
