package com.openclaw.memory.agents.semantic;

import com.openclaw.memory.agents.BaseAgent;
import com.openclaw.memory.blackboard.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Semantic Memory Agent Interface
 * 
 * Ответственность:
 * - Поддерживает дистиллированные факты
 * - Управляет обновлениями убеждений
 * - Обрабатывает оценку уверенности + граф замещения
 */
public interface SemanticMemoryAgent extends BaseAgent {
    
    /**
     * Записать семантический факт
     */
    String recordFact(SemanticFact fact);
    
    /**
     * Получить факт по ID
     */
    SemanticFact getFact(String factId);
    
    /**
     * Обновить факт с новой информацией
     */
    void updateFact(String factId, SemanticFact newFact);
    
    /**
     * Получить все активные факты с уверенностью > threshold
     */
    List<SemanticFact> getActiveFactsByConfidence(float threshold);
    
    /**
     * Найти факты по субъекту или предикату
     */
    List<SemanticFact> searchFacts(String query);
    
    /**
     * Получить историю обновлений факта
     */
    List<FactUpdate> getFactHistory(String factId);
    
    /**
     * Проверить, есть ли суперсессия (устаревание) факта
     */
    List<String> getSupersedingFacts(String factId);
    
    /**
     * Получить статистику семантической памяти
     */
    SemanticMemoryStats getStats();
    
    class SemanticFact {
        public final String factId;
        public final String subject;
        public final String predicate;
        public final String object;
        public final float confidence;
        public final Instant recordedAt;
        public final Instant validFrom;
        public final Instant validTo;
        public final String source;
        public final Map<String, Object> metadata;
        
        public SemanticFact(String id, String subj, String pred, String obj,
                          float conf, Instant from, Instant to, String source,
                          Map<String, Object> meta) {
            this.factId = id;
            this.subject = subj;
            this.predicate = pred;
            this.object = obj;
            this.confidence = conf;
            this.recordedAt = Instant.now();
            this.validFrom = from;
            this.validTo = to;
            this.source = source;
            this.metadata = meta;
        }
    }
    
    class FactUpdate {
        public final String factId;
        public final SemanticFact previousState;
        public final SemanticFact newState;
        public final Instant timestamp;
        public final String reason;
        
        public FactUpdate(String id, SemanticFact prev, SemanticFact next,
                        String reason) {
            this.factId = id;
            this.previousState = prev;
            this.newState = next;
            this.timestamp = Instant.now();
            this.reason = reason;
        }
    }
    
    class SemanticMemoryStats {
        public final long totalFacts;
        public final long activeFacts;
        public final long supersededFacts;
        public final double averageConfidence;
        public final Map<String, Long> factsBySource;
        
        public SemanticMemoryStats(long total, long active, long superseded,
                                 double avgConf, Map<String, Long> bySource) {
            this.totalFacts = total;
            this.activeFacts = active;
            this.supersededFacts = superseded;
            this.averageConfidence = avgConf;
            this.factsBySource = bySource;
        }
    }
}
