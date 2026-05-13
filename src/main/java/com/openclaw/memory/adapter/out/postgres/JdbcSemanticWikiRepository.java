package com.openclaw.memory.adapter.out.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.model.MemoryType;
import com.openclaw.memory.domain.port.SemanticWikiRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.postgresql.util.PGobject;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSemanticWikiRepository implements SemanticWikiRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcSemanticWikiRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public MemoryRecord upsert(String title, MemoryRecord record) {
        Map<String, Object> metadata = new LinkedHashMap<>(record.metadata());
        metadata.put("title", title);

        return jdbcClient.sql("""
                INSERT INTO semantic_wiki_memory (id, agent_id, title, content, metadata, created_at, updated_at)
                VALUES (:id, :agentId, :title, :content, :metadata, :createdAt, :updatedAt)
                ON CONFLICT (agent_id, title)
                DO UPDATE SET content = excluded.content,
                              metadata = excluded.metadata,
                              updated_at = excluded.updated_at
                RETURNING id, agent_id, title, content, metadata, created_at, updated_at
                """)
                .param("id", record.id())
                .param("agentId", record.agentId())
                .param("title", title)
                .param("content", record.content())
                .param("metadata", jsonb(metadata))
                .param("createdAt", Timestamp.from(record.createdAt()))
                .param("updatedAt", Timestamp.from(Instant.now()))
                .query(this::mapRow)
                .single();
    }

    @Override
    public List<MemoryRecord> findRelevant(String agentId, String query, int limit) {
        return jdbcClient.sql("""
                SELECT id, agent_id, title, content, metadata, created_at, updated_at
                FROM semantic_wiki_memory
                WHERE agent_id = :agentId
                  AND (content ILIKE :query OR title ILIKE :query)
                ORDER BY updated_at DESC
                LIMIT :limit
                """)
                .param("agentId", agentId)
                .param("query", "%" + query + "%")
                .param("limit", limit)
                .query(this::mapRow)
                .list();
    }

    private MemoryRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> metadata = new LinkedHashMap<>(readJson(rs.getString("metadata")));
        metadata.putIfAbsent("title", rs.getString("title"));
        return new MemoryRecord(
                rs.getObject("id", UUID.class),
                rs.getString("agent_id"),
                null,
                MemoryType.SEMANTIC_WIKI,
                rs.getString("content"),
                metadata,
                instant(rs, "created_at")
        );
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private PGobject jsonb(Map<String, Object> value) {
        try {
            PGobject object = new PGobject();
            object.setType("jsonb");
            object.setValue(objectMapper.writeValueAsString(value));
            return object;
        } catch (SQLException | JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to encode JSONB value", ex);
        }
    }

    private Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new DataRetrievalFailureException("Unable to decode JSONB value", ex);
        }
    }
}
