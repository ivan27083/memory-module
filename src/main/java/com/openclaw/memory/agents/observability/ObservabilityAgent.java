package com.openclaw.memory.agents.observability;

import com.openclaw.memory.agents.BaseAgent;
import com.openclaw.memory.blackboard.*;

/**
 * Observability Agent Interface
 * 
 * Ответственность:
 * - Собирает метрики и трассы
 * - Логирует решения о поиске
 * - Отслеживает задержки и производительность
 */
public interface ObservabilityAgent extends BaseAgent {
    
    /**
     * Записать метрику
     */
    void recordMetric(String metricName, double value);
    
    /**
     * Записать трассу для отладки
     */
    void recordTrace(String traceId, String event, java.util.Map<String, Object> details);
    
    /**
     * Получить метрики системы
     */
    SystemMetrics getSystemMetrics();
    
    /**
     * Экспортировать метрики в Prometheus формате
     */
    String exportPrometheus();
    
    class SystemMetrics {
        public final long timestamp;
        public final double cpuUsage;
        public final double memoryUsage;
        public final long tasksThroughput;
        public final double averageLatency;
        public final double cacheHitRate;
        public final long eventsProcessed;
        
        public SystemMetrics(long ts, double cpu, double mem, long throughput,
                           double latency, double cache, long events) {
            this.timestamp = ts;
            this.cpuUsage = cpu;
            this.memoryUsage = mem;
            this.tasksThroughput = throughput;
            this.averageLatency = latency;
            this.cacheHitRate = cache;
            this.eventsProcessed = events;
        }
    }
}
