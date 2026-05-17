package com.openclaw.memory.application;

import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.model.VectorDocument;
import com.openclaw.memory.domain.port.EmbeddingClient;
import com.openclaw.memory.domain.port.MemoryConsolidationPort;
import com.openclaw.memory.domain.port.VectorIndex;
import com.openclaw.memory.retrieval.QMDClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MemoryConsolidationService implements MemoryConsolidationPort {

    private static final Logger log = LoggerFactory.getLogger(MemoryConsolidationService.class);

    // Counts consecutive vector failures so repeated WARN logs are suppressed after the first.
    private final AtomicInteger vectorFailures = new AtomicInteger(0);

    private final EmbeddingClient embeddingClient;
    private final VectorIndex vectorIndex;

    // Optional — present only when memory.qmd.enabled=true
    private final QMDClient qmdClient;

    public MemoryConsolidationService(
            EmbeddingClient embeddingClient,
            VectorIndex vectorIndex,
            @Autowired(required = false) QMDClient qmdClient) {
        this.embeddingClient = embeddingClient;
        this.vectorIndex     = vectorIndex;
        this.qmdClient       = qmdClient;
    }

    @Override
    public void indexMemory(MemoryRecord record) {
        indexVector(record);
        indexQmd(record);
    }

    @Override
    public void deleteMemory(UUID id) {
        try {
            vectorIndex.delete(id);
        } catch (RuntimeException ex) {
            log.warn("Failed to delete vector for record {}: {}", id, ex.getMessage());
        }
    }

    // ── Vector (Qdrant) ───────────────────────────────────────────────────────

    private void indexVector(MemoryRecord record) {
        try {
            Map<String, Object> metadata = new LinkedHashMap<>(record.metadata());
            metadata.put("agentId",    record.agentId());
            metadata.put("sourceType", record.type().name());
            if (record.sessionId() != null) {
                metadata.put("sessionId", record.sessionId());
            }

            vectorIndex.upsert(new VectorDocument(
                    record.id(),
                    record.type(),
                    record.content(),
                    embeddingClient.embed(record.content()),
                    metadata
            ));
            vectorFailures.set(0);
        } catch (RuntimeException ex) {
            int count = vectorFailures.incrementAndGet();
            if (count == 1) {
                log.warn("Vector consolidation unavailable for record {} — suppressing further warnings",
                        record.id(), ex);
            } else {
                log.debug("Vector consolidation skipped (failure #{}) for record {}", count, record.id());
            }
        }
    }

    // ── QMD sidecar ───────────────────────────────────────────────────────────

    private void indexQmd(MemoryRecord record) {
        if (qmdClient == null) return;
        try {
            qmdClient.index(record);
        } catch (RuntimeException ex) {
            log.warn("QMD consolidation failed for record {}", record.id(), ex);
        }
    }
}
