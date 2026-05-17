package com.openclaw.memory.graph;

import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.model.MemoryType;
import com.openclaw.memory.domain.port.MemoryGraphPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests for MemoryGraphPort — run against the in-memory implementation.
 * KuzuMemoryGraph satisfies the same contract (activated via memory.graph.backend=kuzu).
 */
class MemoryGraphPortTest {

    private MemoryGraphPort graph;

    @BeforeEach
    void setUp() {
        graph = new TemporalGraphManager();
    }

    // ── addMemory ─────────────────────────────────────────────────────────────

    @Test
    void addMemory_shouldNotThrow() {
        graph.addMemory(record(UUID.randomUUID(), "agent-1"));
    }

    @Test
    void addMemory_shouldBeIdempotent() {
        MemoryRecord r = record(UUID.randomUUID(), "agent-1");
        graph.addMemory(r);
        graph.addMemory(r); // second call must not throw
    }

    // ── isConsistent ─────────────────────────────────────────────────────────

    @Test
    void isConsistent_shouldReturnTrue_forFreshMemory() {
        UUID id = UUID.randomUUID();
        graph.addMemory(record(id, "agent-1"));
        assertThat(graph.isConsistent(id.toString(), LocalDateTime.now())).isTrue();
    }

    @Test
    void isConsistent_shouldReturnFalse_afterSupersession() {
        UUID oldId = UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        graph.addMemory(record(oldId, "agent-1"));
        graph.addMemory(record(newId, "agent-1"));

        graph.recordSupersession(oldId, newId, Instant.now());

        // oldId has been superseded — no longer consistent
        assertThat(graph.isConsistent(oldId.toString(), LocalDateTime.now())).isFalse();
        // newId is the current head — still consistent
        assertThat(graph.isConsistent(newId.toString(), LocalDateTime.now())).isTrue();
    }

    // ── getSupersessionChain ──────────────────────────────────────────────────

    @Test
    void getSupersessionChain_shouldReturnEmpty_forHead() {
        UUID id = UUID.randomUUID();
        graph.addMemory(record(id, "agent-1"));
        assertThat(graph.getSupersessionChain(id.toString())).isEmpty();
    }

    @Test
    void getSupersessionChain_shouldReturnSuccessors() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        graph.addMemory(record(a, "agent-1"));
        graph.addMemory(record(b, "agent-1"));
        graph.addMemory(record(c, "agent-1"));

        graph.recordSupersession(a, b, Instant.now());
        graph.recordSupersession(b, c, Instant.now());

        List<String> chain = graph.getSupersessionChain(a.toString());

        assertThat(chain).contains(b.toString(), c.toString());
    }

    // ── recordSupersession ────────────────────────────────────────────────────

    @Test
    void recordSupersession_shouldNotThrow_forUnknownIds() {
        // Best-effort: should not throw even if nodes aren't indexed first
        graph.recordSupersession(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static MemoryRecord record(UUID id, String agentId) {
        return new MemoryRecord(id, agentId, null, MemoryType.EPISODIC,
                "content", Map.of(), Instant.now(), Instant.now(), null, null);
    }
}
