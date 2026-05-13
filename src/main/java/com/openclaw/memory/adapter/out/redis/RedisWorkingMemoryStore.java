package com.openclaw.memory.adapter.out.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.port.WorkingMemoryStore;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisWorkingMemoryStore implements WorkingMemoryStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisWorkingMemoryStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public MemoryRecord save(MemoryRecord record, Duration ttl) {
        String key = key(record.agentId(), record.sessionId());
        redisTemplate.opsForList().leftPush(key, encode(record));
        redisTemplate.opsForList().trim(key, 0, 199);
        redisTemplate.expire(key, ttl);
        return record;
    }

    @Override
    public List<MemoryRecord> findRecent(String agentId, String sessionId, int limit) {
        List<String> values = redisTemplate.opsForList().range(key(agentId, sessionId), 0, Math.max(limit - 1, 0));
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(this::decode)
                .toList();
    }

    private String encode(MemoryRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to encode working memory record", ex);
        }
    }

    private MemoryRecord decode(String json) {
        try {
            return objectMapper.readValue(json, MemoryRecord.class);
        } catch (JsonProcessingException ex) {
            throw new DataRetrievalFailureException("Unable to decode working memory record", ex);
        }
    }

    private static String key(String agentId, String sessionId) {
        return "memory:working:" + agentId + ":" + (sessionId == null ? "global" : sessionId);
    }
}
