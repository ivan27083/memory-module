package com.openclaw.memory.application;

import com.openclaw.memory.config.MemoryModuleProperties;
import com.openclaw.memory.domain.port.EpisodicMemoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MemoryMaintenanceServiceTest {

    private final EpisodicMemoryRepository episodicRepo = mock(EpisodicMemoryRepository.class);

    private MemoryMaintenanceService serviceWith(Duration retention) {
        MemoryModuleProperties props = new MemoryModuleProperties(
                Duration.ofHours(24), 12,
                new MemoryModuleProperties.Http(Duration.ofSeconds(3), Duration.ofSeconds(20)),
                new MemoryModuleProperties.Vector("agent_memory", 768, "http://localhost:6333"),
                new MemoryModuleProperties.Embedding("http://localhost:1234/v1", "test-model", "local"),
                new MemoryModuleProperties.Maintenance(retention, "0 0 3 * * *")
        );
        return new MemoryMaintenanceService(episodicRepo, props);
    }

    @Test
    void prune_callsDeleteOlderThan_withCorrectCutoff() {
        Duration retention = Duration.ofDays(30);
        when(episodicRepo.deleteOlderThan(any())).thenReturn(0);

        Instant before = Instant.now().minus(retention);
        serviceWith(retention).pruneEpisodicMemory();
        Instant after = Instant.now().minus(retention);

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(episodicRepo).deleteOlderThan(captor.capture());

        Instant cutoff = captor.getValue();
        assertThat(cutoff).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
    }

    @Test
    void prune_shorterRetention_usesLaterCutoff() {
        // 7-day retention  → cutoff = now - 7d  (more recent, later in time)
        // 30-day retention → cutoff = now - 30d (further back, earlier in time)
        when(episodicRepo.deleteOlderThan(any())).thenReturn(0);

        serviceWith(Duration.ofDays(7)).pruneEpisodicMemory();
        serviceWith(Duration.ofDays(30)).pruneEpisodicMemory();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(episodicRepo, times(2)).deleteOlderThan(captor.capture());

        List<Instant> cutoffs = captor.getAllValues();
        Instant cutoff7  = cutoffs.get(0);
        Instant cutoff30 = cutoffs.get(1);

        assertThat(cutoff30).isBefore(cutoff7);
    }

    @Test
    void prune_returnsDeletedCount_doesNotThrow() {
        when(episodicRepo.deleteOlderThan(any())).thenReturn(42);
        serviceWith(Duration.ofDays(30)).pruneEpisodicMemory();
        verify(episodicRepo, times(1)).deleteOlderThan(any());
    }

    @Test
    void prune_repoReturnsZero_doesNotThrow() {
        when(episodicRepo.deleteOlderThan(any())).thenReturn(0);
        serviceWith(Duration.ofDays(90)).pruneEpisodicMemory();
        verify(episodicRepo).deleteOlderThan(any());
    }
}
