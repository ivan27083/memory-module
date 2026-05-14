package com.openclaw.memory.config;

import com.openclaw.memory.blackboard.MemoryBlackboard;
import com.openclaw.memory.event_store.*;
import com.openclaw.memory.agents.orchestrator.*;
import com.openclaw.memory.agents.event_store.EventStoreAgentImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spring Configuration для инициализации всех компонентов MACMR
 */
@Configuration
public class MemoryModuleConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(MemoryModuleConfiguration.class);
    
    /**
     * Инициализировать Singleton Blackboard
     */
    @Bean
    public MemoryBlackboard memoryBlackboard() {
        logger.info("Initializing Memory Blackboard");
        return MemoryBlackboard.getInstance();
    }
    
    /**
     * Инициализировать Event Store (in-memory для начала)
     */
    @Bean
    public EventStore eventStore() {
        logger.info("Initializing Event Store (InMemory)");
        return new InMemoryEventStore();
    }
    
    /**
     * Инициализировать Orchestrator Agent
     */
    @Bean
    public OrchestratorAgent orchestratorAgent(
            MemoryBlackboard blackboard,
            EventStore eventStore) {
        logger.info("Initializing Orchestrator Agent");
        
        OrchestratorAgent agent = new DefaultOrchestratorAgent(blackboard, eventStore);
        agent.initialize();
        
        return agent;
    }
    
    /**
     * Инициализировать Event Store Agent
     */
    @Bean
    public EventStoreAgentImpl eventStoreAgent(
            EventStore eventStore,
            MemoryBlackboard blackboard) {
        logger.info("Initializing Event Store Agent");
        
        EventStoreAgentImpl agent = new EventStoreAgentImpl(eventStore, blackboard);
        agent.initialize();
        
        return agent;
    }
}
