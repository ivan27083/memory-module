package com.openclaw.memory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.port.MemoryGraphPort;
import com.openclaw.memory.event_store.EventStore;
import com.openclaw.memory.event_store.InMemoryEventStore;
import com.openclaw.memory.retrieval.QMDClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableScheduling
public class MemoryModuleConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MemoryModuleConfiguration.class);

    @Bean
    public EventStore eventStore() {
        logger.info("Initializing Event Store (InMemory)");
        return new InMemoryEventStore();
    }

    @Bean
    @ConditionalOnProperty(name = "memory.qmd.enabled", havingValue = "true")
    public QMDClient qmdClient(QMDClientProperties props, ObjectMapper mapper) {
        logger.info("Initializing QMDClient → {}", props.getBaseUrl());
        return new QMDClient(props, mapper);
    }

    @Bean
    @ConditionalOnProperty(name = "memory.graph.backend", havingValue = "in-memory", matchIfMissing = true)
    public MemoryGraphPort inMemoryGraph() {
        logger.info("Initializing MemoryGraphPort: in-memory (no-op)");
        return new MemoryGraphPort() {
            @Override public void addMemory(MemoryRecord record) {}
            @Override public void recordSupersession(UUID oldId, UUID newId, Instant at) {}
            @Override public List<String> getSupersessionChain(String memoryId) { return List.of(); }
            @Override public boolean isConsistent(String artifactId, LocalDateTime atTime) { return true; }
        };
    }
}
