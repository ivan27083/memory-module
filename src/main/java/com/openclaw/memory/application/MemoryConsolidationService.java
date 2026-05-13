package com.openclaw.memory.application;

import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.model.VectorDocument;
import com.openclaw.memory.domain.port.EmbeddingClient;
import com.openclaw.memory.domain.port.VectorIndex;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class MemoryConsolidationService {

    private static final Logger log = LoggerFactory.getLogger(MemoryConsolidationService.class);

    private final EmbeddingClient embeddingClient;
    private final VectorIndex vectorIndex;

    public MemoryConsolidationService(EmbeddingClient embeddingClient, VectorIndex vectorIndex) {
        this.embeddingClient = embeddingClient;
        this.vectorIndex = vectorIndex;
    }

    @Async
    public void indexMemory(MemoryRecord record) {
        try {
            Map<String, Object> metadata = new LinkedHashMap<>(record.metadata());
            metadata.put("agentId", record.agentId());
            if (record.sessionId() != null) {
                metadata.put("sessionId", record.sessionId());
            }
            metadata.put("sourceType", record.type().name());

            vectorIndex.upsert(new VectorDocument(
                    record.id(),
                    record.type(),
                    record.content(),
                    embeddingClient.embed(record.content()),
                    metadata
            ));
        } catch (RuntimeException ex) {
            log.warn("Memory consolidation failed for record {}", record.id(), ex);
        }
    }
}
