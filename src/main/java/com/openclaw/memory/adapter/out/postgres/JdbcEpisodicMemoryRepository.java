package com.openclaw.memory.adapter.out.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.model.MemoryType;
import com.openclaw.memory.domain.port.EpisodicMemoryRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.postgresql.util.PGobject;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEpisodicMemoryRepository implements EpisodicMemoryRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcEpisodicMemoryRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public MemoryRecord save(MemoryRecord record) {
        jdbcClient.sql("""
                INSERT INTO episodic_memory (id, agent_id, session_id, content, metadata, created_at)
                VALUES (:id, :agentId, :sessionId, :content, :metadata, :createdAt)
                """)
                .param("id", record.id())
                .param("agentId", record.agentId())
                .param("sessionId", record.sessionId())
                .param("content", record.content())
                .param("metadata", jsonb(record.metadata()))
                .param("createdAt", Timestamp.from(record.createdAt()))
                .update();
        return new MemoryRecord(record.id(), record.agentId(), record.sessionId(), MemoryType.EPISODIC,
                record.content(), record.metadata(), record.createdAt());
    }

    @Override
    public List<MemoryRecord> findRecent(String agentId, String sessionId, int limit) {
        if (sessionId == null || sessionId.isBlank()) {
            return jdbcClient.sql("""
                    SELECT id, agent_id, session_id, content, metadata, created_at
                    FROM episodic_memory
                    WHERE agent_id = :agentId
                    ORDER BY created_at DESC
                    LIMIT :limit
                    """)
                    .param("agentId", agentId)
                    .param("limit", limit)
                    .query(this::mapRow)
                    .list();
        }

        return jdbcClient.sql("""
                SELECT id, agent_id, session_id, content, metadata, created_at
                FROM episodic_memory
                WHERE agent_id = :agentId AND session_id = :sessionId
                ORDER BY created_at DESC
                LIMIT :limit
                """)
                .param("agentId", agentId)
                .param("sessionId", sessionId)
                .param("limit", limit)
                .query(this::mapRow)
                .list();
    }

    private MemoryRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new MemoryRecord(
                rs.getObject("id", UUID.class),
                rs.getString("agent_id"),
                rs.getString("session_id"),
                MemoryType.EPISODIC,
                rs.getString("content"),
                readJson(rs.getString("metadata")),
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
