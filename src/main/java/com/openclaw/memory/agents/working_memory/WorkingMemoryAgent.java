package com.openclaw.memory.agents.working_memory;

import com.openclaw.memory.agents.BaseAgent;
import com.openclaw.memory.blackboard.*;
import java.util.List;
import java.util.Map;

/**
 * Working Memory Agent Interface
 * 
 * Ответственность:
 * - Реконструирует контекст выполнения
 * - Формирует финальное приглашение для агента
 * - Разрешает противоречия во время вывода
 */
public interface WorkingMemoryAgent extends BaseAgent {
    
    /**
     * Собрать и составить контекст для агента
     */
    ExecutionContext buildContext(String agentName, String task, List<String> relevantArtifactIds);
    
    /**
     * Получить активные факты из семантической памяти
     */
    List<String> getActiveFacts();
    
    /**
     * Получить недавние события
     */
    List<String> getRecentEvents(int count);
    
    /**
     * Получить релевантные результаты поиска
     */
    List<String> getRetrievalResults(String query);
    
    /**
     * Разрешить противоречия между источниками
     */
    ResolvedContext resolveContradictions(ExecutionContext context);
    
    /**
     * Проверить консистентность контекста
     */
    boolean isContextConsistent(ExecutionContext context);
    
    /**
     * Получить рейтинг релевантности источника
     */
    double getSourceReliability(String sourceId);
    
    class ExecutionContext {
        public final String contextId;
        public final String targetAgent;
        public final String task;
        public final List<String> activeFacts;
        public final List<String> recentEvents;
        public final List<String> retrievedDocuments;
        public final Map<String, String> entityResolutions;
        public final String composedPrompt;
        
        public ExecutionContext(String id, String agent, String task,
                              List<String> facts, List<String> events,
                              List<String> documents, Map<String, String> entities,
                              String prompt) {
            this.contextId = id;
            this.targetAgent = agent;
            this.task = task;
            this.activeFacts = facts;
            this.recentEvents = events;
            this.retrievedDocuments = documents;
            this.entityResolutions = entities;
            this.composedPrompt = prompt;
        }
    }
    
    class ResolvedContext {
        public final ExecutionContext context;
        public final List<String> contradictionsFound;
        public final List<String> resolutions;
        public final float trustScore;
        
        public ResolvedContext(ExecutionContext ctx, List<String> contradictions,
                             List<String> resolutions, float trust) {
            this.context = ctx;
            this.contradictionsFound = contradictions;
            this.resolutions = resolutions;
            this.trustScore = trust;
        }
    }
}
