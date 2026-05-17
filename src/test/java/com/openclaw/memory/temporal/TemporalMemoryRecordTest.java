package com.openclaw.memory.temporal;

import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.model.MemoryType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TemporalMemoryRecordTest {

    // ── Factory / defaults ────────────────────────────────────────────────────

    @Test
    void create_shouldDefaultValidFromToCreatedAt() {
        Instant now = Instant.now();
        MemoryRecord r = MemoryRecord.create(null, "agent", null, MemoryType.EPISODIC, "x", Map.of(), now);

        assertThat(r.validFrom()).isEqualTo(now);
        assertThat(r.validTo()).isNull();
        assertThat(r.supersededBy()).isNull();
    }

    @Test
    void create_shouldGenerateIdWhenNull() {
        MemoryRecord r = MemoryRecord.create(null, "agent", null, MemoryType.EPISODIC, "x", Map.of(), null);
        assertThat(r.id()).isNotNull();
    }

    // ── isActive ──────────────────────────────────────────────────────────────

    @Test
    void isActive_shouldBeTrueForFreshRecord() {
        MemoryRecord r = record();
        assertThat(r.isActive()).isTrue();
    }

    @Test
    void isActive_shouldBeFalseWhenSuperseded() {
        MemoryRecord r = record().supersede(UUID.randomUUID());
        assertThat(r.isActive()).isFalse();
    }

    @Test
    void isActive_shouldBeFalseWhenValidToIsSet() {
        MemoryRecord r = new MemoryRecord(
                UUID.randomUUID(), "agent", null, MemoryType.EPISODIC,
                "content", Map.of(), Instant.now(),
                Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().minus(1, ChronoUnit.MINUTES),   // already expired
                null
        );
        assertThat(r.isActive()).isFalse();
    }

    // ── isValidAt ─────────────────────────────────────────────────────────────

    @Test
    void isValidAt_openEndedRecord_shouldBeValidAtAnyFutureTime() {
        MemoryRecord r = record();
        assertThat(r.isValidAt(Instant.now())).isTrue();
        assertThat(r.isValidAt(Instant.now().plus(365, ChronoUnit.DAYS))).isTrue();
    }

    @Test
    void isValidAt_shouldBeFalseBeforeValidFrom() {
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
        MemoryRecord r = new MemoryRecord(
                UUID.randomUUID(), "agent", null, MemoryType.EPISODIC,
                "content", Map.of(), Instant.now(),
                future,     // validFrom is in the future
                null, null
        );
        assertThat(r.isValidAt(Instant.now())).isFalse();
    }

    @Test
    void isValidAt_shouldBeFalseAfterValidTo() {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        MemoryRecord r = new MemoryRecord(
                UUID.randomUUID(), "agent", null, MemoryType.EPISODIC,
                "content", Map.of(), past,
                past,
                Instant.now().minus(1, ChronoUnit.MINUTES),   // validTo already passed
                null
        );
        assertThat(r.isValidAt(Instant.now())).isFalse();
    }

    // ── supersede ─────────────────────────────────────────────────────────────

    @Test
    void supersede_shouldCloseValidityWindowAndSetPointer() {
        MemoryRecord original = record();
        UUID newId = UUID.randomUUID();

        MemoryRecord closed = original.supersede(newId);

        assertThat(closed.supersededBy()).isEqualTo(newId);
        assertThat(closed.validTo()).isNotNull();
        assertThat(closed.validTo()).isBeforeOrEqualTo(Instant.now());
        assertThat(closed.isActive()).isFalse();
    }

    @Test
    void supersede_shouldPreserveAllOtherFields() {
        MemoryRecord original = record();
        MemoryRecord closed = original.supersede(UUID.randomUUID());

        assertThat(closed.id()).isEqualTo(original.id());
        assertThat(closed.agentId()).isEqualTo(original.agentId());
        assertThat(closed.content()).isEqualTo(original.content());
        assertThat(closed.createdAt()).isEqualTo(original.createdAt());
        assertThat(closed.validFrom()).isEqualTo(original.validFrom());
    }

    // ── withMetadata ──────────────────────────────────────────────────────────

    @Test
    void withMetadata_shouldPreserveTemporalFields() {
        Instant validFrom = Instant.now().minus(10, ChronoUnit.MINUTES);
        Instant validTo   = Instant.now().plus(1, ChronoUnit.HOURS);
        UUID    suppBy    = UUID.randomUUID();

        MemoryRecord r = new MemoryRecord(
                UUID.randomUUID(), "agent", null, MemoryType.EPISODIC,
                "content", Map.of(), Instant.now(), validFrom, validTo, suppBy
        );

        MemoryRecord updated = r.withMetadata("key", "value");

        assertThat(updated.validFrom()).isEqualTo(validFrom);
        assertThat(updated.validTo()).isEqualTo(validTo);
        assertThat(updated.supersededBy()).isEqualTo(suppBy);
        assertThat(updated.metadata()).containsEntry("key", "value");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static MemoryRecord record() {
        return MemoryRecord.create(
                UUID.randomUUID(), "agent-1", "sess-1",
                MemoryType.EPISODIC, "some content", Map.of(), Instant.now());
    }
}
