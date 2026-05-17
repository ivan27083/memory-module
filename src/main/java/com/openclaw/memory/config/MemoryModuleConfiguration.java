package com.openclaw.memory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.memory.agents.conflict.SubjectConflictResolver;
import com.openclaw.memory.blackboard.MemoryBlackboard;
import com.openclaw.memory.domain.port.MemoryGraphPort;
import com.openclaw.memory.event_store.*;
import com.openclaw.memory.graph.TemporalGraphManager;
import com.openclaw.memory.retrieval.*;
import com.openclaw.memory.working_memory.WorkingMemoryComposer;
import com.openclaw.memory.agents.orchestrator.*;
import com.openclaw.memory.agents.event_store.EventStoreAgentImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableScheduling
public class MemoryModuleConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MemoryModuleConfiguration.class);

    // ── Infrastructure beans ──────────────────────────────────────────────────

    @Bean
    public MemoryBlackboard memoryBlackboard() {
        logger.info("Initializing Memory Blackboard");
        return MemoryBlackboard.getInstance();
    }

    @Bean
    public EventStore eventStore() {
        logger.info("Initializing Event Store (InMemory)");
        return new InMemoryEventStore();
    }

    @Bean
    public OrchestratorAgent orchestratorAgent(MemoryBlackboard blackboard, EventStore eventStore) {
        logger.info("Initializing Orchestrator Agent");
        OrchestratorAgent agent = new DefaultOrchestratorAgent(blackboard, eventStore);
        agent.initialize();
        return agent;
    }

    @Bean
    public EventStoreAgentImpl eventStoreAgent(EventStore eventStore, MemoryBlackboard blackboard) {
        logger.info("Initializing Event Store Agent");
        EventStoreAgentImpl agent = new EventStoreAgentImpl(eventStore, blackboard);
        agent.initialize();
        return agent;
    }

    // ── QMD sidecar client (optional) ────────────────────────────────────────

    @Bean
    @ConditionalOnProperty(name = "memory.qmd.enabled", havingValue = "true")
    public QMDClient qmdClient(QMDClientProperties props, ObjectMapper mapper) {
        logger.info("Initializing QMDClient → {}", props.getBaseUrl());
        return new QMDClient(props, mapper);
    }

    // ── Retriever selection ───────────────────────────────────────────────────

    @Bean
    @Primary
    public Retriever retriever(
            QMDRetrievalEngine engine,
            MemoryBlackboard blackboard,
            RetrieverProperties props,
            // Injected only when memory.qmd.enabled=true
            org.springframework.beans.factory.ObjectProvider<QMDClient> qmdClientProvider) {

        String provider = props.getProvider();
        logger.info("Initializing Retriever: provider={}", provider);

        Retriever base = switch (provider.toLowerCase()) {
            case "qmd-http" -> {
                QMDClient client = qmdClientProvider.getIfAvailable();
                if (client == null) {
                    logger.warn("provider=qmd-http but memory.qmd.enabled is false — falling back to BlackboardRetriever");
                    yield new BlackboardRetriever(blackboard);
                }
                logger.info("Using QMDHttpRetriever → {}", client);
                yield new QMDHttpRetriever(client);
            }
            case "qmd" -> {
                logger.info("Using QMDRetrieverAdapter (Java engine)");
                yield new QMDRetrieverAdapter(engine);
            }
            default -> {
                logger.warn("Unknown provider '{}' — falling back to BlackboardRetriever", provider);
                yield new BlackboardRetriever(blackboard);
            }
        };

        if (props.isCacheEnabled()) {
            logger.info("Wrapping Retriever with CachingRetriever (ttl={}min)", props.getCacheTtlMinutes());
            return new CachingRetriever(base, props.getCacheTtlMinutes());
        }
        return base;
    }

    // ── MemoryGraphPort — in-memory fallback (overridden by KuzuMemoryGraph when kuzu is active) ──

    @Bean
    @ConditionalOnProperty(name = "memory.graph.backend", havingValue = "in-memory", matchIfMissing = true)
    public MemoryGraphPort inMemoryGraph(TemporalGraphManager graphManager) {
        logger.info("Initializing MemoryGraphPort: in-memory (TemporalGraphManager)");
        return graphManager;
    }

    // ── WorkingMemoryComposer ─────────────────────────────────────────────────

    @Bean
    public WorkingMemoryComposer workingMemoryComposer(
            Retriever retriever,
            MemoryGraphPort graphPort,
            SubjectConflictResolver conflictResolver) {
        logger.info("Initializing WorkingMemoryComposer with SubjectConflictResolver");
        return new WorkingMemoryComposer(
                retriever,
                conflictResolver,
                // Null artifact (QMD path) is always treated as valid
                (artifact, atTime) -> artifact == null
                        || graphPort.isConsistent(artifact.getArtifactId(), atTime),
                4000
        );
    }
}
