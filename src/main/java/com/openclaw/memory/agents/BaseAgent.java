package com.openclaw.memory.agents;

import com.openclaw.memory.blackboard.Task;
import com.openclaw.memory.blackboard.Artifact;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * BaseAgent - базовый интерфейс для всех агентов в системе
 */
public interface BaseAgent {
    
    /**
     * Имя агента для идентификации в системе
     */
    String getName();
    
    /**
     * Описание отвественности и функций агента
     */
    String getDescription();
    
    /**
     * Инициализирует агента при запуске системы
     */
    void initialize();
    
    /**
     * Останавливает агента корректно
     */
    void shutdown();
    
    /**
     * Может ли этот агент обработать задачу
     */
    boolean canHandle(Task task);
    
    /**
     * Асинхронно выполняет задачу
     */
    CompletableFuture<List<Artifact>> executeTask(Task task);
    
    /**
     * Обработать ошибку при выполнении задачи
     */
    void handleFailure(Task task, Exception error);
    
    /**
     * Получить статус агента
     */
    AgentStatus getStatus();
    
    /**
     * Метрики производительности агента
     */
    AgentMetrics getMetrics();
    
    enum AgentStatus {
        INITIALIZING,
        READY,
        BUSY,
        ERROR,
        SHUTDOWN
    }
    
    class AgentMetrics {
        public final int tasksCompleted;
        public final int tasksFailed;
        public final long averageExecutionTimeMs;
        public final double successRate;
        public final long lastExecutionTimeMs;
        
        public AgentMetrics(int completed, int failed, long avgTime, 
                          double successRate, long lastTime) {
            this.tasksCompleted = completed;
            this.tasksFailed = failed;
            this.averageExecutionTimeMs = avgTime;
            this.successRate = successRate;
            this.lastExecutionTimeMs = lastTime;
        }
    }
}
