package com.openclaw.memory.agents.orchestrator;

import com.openclaw.memory.agents.BaseAgent;
import com.openclaw.memory.blackboard.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestrator Agent Interface
 * 
 * Ответственность:
 * - Декомпозирует все задачи системы
 * - Назначает задачи специализированным агентам
 * - Контролирует порядок выполнения
 * - Валидирует завершение гейтов
 */
public interface OrchestratorAgent extends BaseAgent {
    
    /**
     * Декомпозировать высокоуровневую цель на атомарные задачи
     */
    List<Task> decomposeObjective(String objective, List<String> context);
    
    /**
     * Назначить задачи агентам на основе их специализации
     */
    void assignTasks(List<Task> tasks);
    
    /**
     * Проверить, может ли задача быть выполнена в текущем состоянии системы
     */
    boolean canExecuteTask(Task task);
    
    /**
     * Получить зависимости задачи
     */
    List<Task> getTaskDependencies(Task task);
    
    /**
     * Проверить, завершены ли все зависимости задачи
     */
    boolean areDependenciesSatisfied(Task task);
    
    /**
     * Получить оптимальный порядок выполнения задач (топологическая сортировка)
     */
    List<Task> getOptimalExecutionOrder(List<Task> tasks);
    
    /**
     * Валидировать состояние системы после выполнения задач
     */
    boolean validateSystemState();
    
    /**
     * Получить текущий план выполнения
     */
    ExecutionPlan getCurrentExecutionPlan();
    
    /**
     * Перепланировать при обнаружении ошибок
     */
    void replan(String failureReason);
    
    /**
     * Статистика оркестратора
     */
    OrchestratorStats getOrchestratorStats();
    
    class ExecutionPlan {
        public final String planId;
        public final List<Task> tasks;
        public final int totalSteps;
        public final int completedSteps;
        public final double estimatedCompletionMs;
        
        public ExecutionPlan(String id, List<Task> tasks, int completed, 
                            double estimatedTime) {
            this.planId = id;
            this.tasks = tasks;
            this.totalSteps = tasks.size();
            this.completedSteps = completed;
            this.estimatedCompletionMs = estimatedTime;
        }
        
        public double getProgress() {
            return totalSteps == 0 ? 0 : (double) completedSteps / totalSteps;
        }
    }
    
    class OrchestratorStats {
        public final long plansCreated;
        public final long tasksDecomposed;
        public final long tasksAssigned;
        public final double successRate;
        public final long averageDecompositionTimeMs;
        
        public OrchestratorStats(long plans, long decomposed, long assigned,
                               double success, long avgTime) {
            this.plansCreated = plans;
            this.tasksDecomposed = decomposed;
            this.tasksAssigned = assigned;
            this.successRate = success;
            this.averageDecompositionTimeMs = avgTime;
        }
    }
}
