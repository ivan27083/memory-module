package com.openclaw.memory.domain.port;

import com.openclaw.memory.domain.model.MemoryRecord;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Port for the temporal memory graph.
 *
 * Implementations: {@code KuzuMemoryGraph} (embedded, persistent)
 *                  {@code TemporalGraphManager} (in-memory, default fallback)
 */
public interface MemoryGraphPort {

    /** Index a written memory as a node. Idempotent on duplicate id. */
    void addMemory(MemoryRecord record);

    /** Record that {@code oldId} was superseded by {@code newId} at the given instant. */
    void recordSupersession(UUID oldId, UUID newId, Instant at);

    /**
     * Walk the SUPERSEDES chain starting at {@code memoryId} and return all
     * subsequent record IDs in chronological order (oldest superseded first).
     */
    List<String> getSupersessionChain(String memoryId);

    /**
     * Returns {@code true} when the given artifact has NOT been superseded
     * (i.e. it is the current head of its supersession chain at {@code atTime}).
     */
    boolean isConsistent(String artifactId, LocalDateTime atTime);
}
