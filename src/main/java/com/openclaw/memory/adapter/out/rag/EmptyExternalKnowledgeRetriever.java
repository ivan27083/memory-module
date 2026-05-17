package com.openclaw.memory.adapter.out.rag;

import com.openclaw.memory.domain.model.MemoryType;
import com.openclaw.memory.domain.model.RetrievalQuery;
import com.openclaw.memory.domain.model.RetrievalResult;
import com.openclaw.memory.domain.port.EmbeddingClient;
import com.openclaw.memory.domain.port.ExternalKnowledgeRetriever;
import com.openclaw.memory.domain.port.VectorIndex;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EmptyExternalKnowledgeRetriever implements ExternalKnowledgeRetriever {

    private final EmbeddingClient embeddingClient;
    private final VectorIndex vectorIndex;

    public EmptyExternalKnowledgeRetriever(EmbeddingClient embeddingClient, VectorIndex vectorIndex) {
        this.embeddingClient = embeddingClient;
        this.vectorIndex = vectorIndex;
    }

    @Override
    public List<RetrievalResult> retrieve(RetrievalQuery query) {
        return vectorIndex.search(
                embeddingClient.embed(query.prompt()),
                Math.max(query.limit(), 1),
                Map.of(
                    "sourceType", MemoryType.EXTERNAL_RAG.name(),
                    "agentId", query.agentId()
                )
        );
    }
}
