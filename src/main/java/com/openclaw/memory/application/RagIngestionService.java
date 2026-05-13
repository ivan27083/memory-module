package com.openclaw.memory.application;

import com.openclaw.memory.domain.model.DocumentChunk;
import com.openclaw.memory.domain.model.MemoryType;
import com.openclaw.memory.domain.model.VectorDocument;
import com.openclaw.memory.domain.port.EmbeddingClient;
import com.openclaw.memory.domain.port.VectorIndex;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RagIngestionService {

    private static final Logger log = LoggerFactory.getLogger(RagIngestionService.class);
    private static final int MAX_CHUNK_SIZE = 1_200;

    private final EmbeddingClient embeddingClient;
    private final VectorIndex vectorIndex;

    public RagIngestionService(EmbeddingClient embeddingClient, VectorIndex vectorIndex) {
        this.embeddingClient = embeddingClient;
        this.vectorIndex = vectorIndex;
    }

    public List<DocumentChunk> ingest(String source, String title, String content, Map<String, Object> metadata) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(title) || !StringUtils.hasText(content)) {
            throw new IllegalArgumentException("source, title and content are required");
        }

        List<DocumentChunk> chunks = chunk(source, title, content, metadata == null ? Map.of() : metadata);
        log.info("Starting RAG ingestion for source={}, title={}, chunks={}", source, title, chunks.size());
        for (DocumentChunk chunk : chunks) {
            Map<String, Object> payload = new LinkedHashMap<>(chunk.metadata());
            payload.put("source", chunk.source());
            payload.put("title", chunk.title());
            payload.put("ordinal", chunk.ordinal());
            log.info("Embedding RAG chunk id={}, ordinal={}", chunk.id(), chunk.ordinal());
            List<Double> embedding = embeddingClient.embed(chunk.content());
            log.info("Indexing RAG chunk id={}, ordinal={}, dimensions={}", chunk.id(), chunk.ordinal(), embedding.size());
            vectorIndex.upsert(new VectorDocument(
                    chunk.id(),
                    MemoryType.EXTERNAL_RAG,
                    chunk.content(),
                    embedding,
                    payload
            ));
        }
        log.info("Finished RAG ingestion for source={}, title={}", source, title);
        return chunks;
    }

    private static List<DocumentChunk> chunk(String source, String title, String content, Map<String, Object> metadata) {
        List<DocumentChunk> chunks = new ArrayList<>();
        UUID documentId = UUID.randomUUID();
        int ordinal = 0;
        for (int offset = 0; offset < content.length(); offset += MAX_CHUNK_SIZE) {
            int end = Math.min(offset + MAX_CHUNK_SIZE, content.length());
            chunks.add(new DocumentChunk(
                    UUID.nameUUIDFromBytes((documentId + ":" + ordinal).getBytes()),
                    source,
                    title,
                    ordinal,
                    content.substring(offset, end),
                    metadata
            ));
            ordinal++;
        }
        return chunks;
    }
}
