package com.openclaw.memory.application;

import com.openclaw.memory.config.MemoryModuleProperties;
import com.openclaw.memory.domain.port.EpisodicMemoryRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MemoryMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MemoryMaintenanceService.class);

    private final EpisodicMemoryRepository episodicRepo;
    private final MemoryModuleProperties properties;

    public MemoryMaintenanceService(EpisodicMemoryRepository episodicRepo,
                                    MemoryModuleProperties properties) {
        this.episodicRepo = episodicRepo;
        this.properties   = properties;
    }

    @Scheduled(cron = "${memory.maintenance.cleanup-cron}")
    public void pruneEpisodicMemory() {
        Instant cutoff = Instant.now().minus(properties.maintenance().episodicRetention());
        log.info("Pruning episodic memories older than {}", cutoff);
        int deleted = episodicRepo.deleteOlderThan(cutoff);
        log.info("Pruned {} episodic memory record(s)", deleted);
    }
}
