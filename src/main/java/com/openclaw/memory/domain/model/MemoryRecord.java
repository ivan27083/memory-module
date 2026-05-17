package com.openclaw.memory.domain.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record MemoryRecord(
        UUID id,
        String agentId,
        String sessionId,
        MemoryType type,
        String content,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant validFrom,      // start of validity window (default = createdAt)
        Instant validTo,        // end of validity window   (null = still valid)
        UUID supersededBy       // ID of the record that replaced this one (null = active)
) {
    public MemoryRecord {
        id          = id        == null ? UUID.randomUUID() : id;
        metadata    = metadata  == null ? Map.of() : Map.copyOf(metadata);
        createdAt   = createdAt == null ? Instant.now() : createdAt;
        validFrom   = validFrom == null ? createdAt : validFrom;
        // validTo and supersededBy intentionally left null-able
    }

    // ── Convenience factory (backward-compatible 7-arg form) ─────────────────

    public static MemoryRecord create(
            UUID id, String agentId, String sessionId,
            MemoryType type, String content,
            Map<String, Object> metadata, Instant createdAt) {
        return new MemoryRecord(id, agentId, sessionId, type, content,
                metadata, createdAt, null, null, null);
    }

    // ── Temporal helpers ──────────────────────────────────────────────────────

    /** True when this record has not been superseded and is within its validity window. */
    public boolean isActive() {
        return supersededBy == null && validTo == null;
    }

    /** True when this record's validity window covers the given instant. */
    public boolean isValidAt(Instant time) {
        return !time.isBefore(validFrom)
                && (validTo == null || time.isBefore(validTo));
    }

    /**
     * Returns a copy of this record closed at now(), pointing at {@code newId}.
     * Used by the repository when a newer record supersedes this one.
     */
    public MemoryRecord supersede(UUID newId) {
        Instant now = Instant.now();
        return new MemoryRecord(id, agentId, sessionId, type, content,
                metadata, createdAt, validFrom, now, newId);
    }

    // ── Metadata mutation (preserves all temporal fields) ────────────────────

    public MemoryRecord withMetadata(String key, Object value) {
        Map<String, Object> next = new LinkedHashMap<>(metadata);
        next.put(key, value);
        return new MemoryRecord(id, agentId, sessionId, type, content,
                next, createdAt, validFrom, validTo, supersededBy);
    }
}
