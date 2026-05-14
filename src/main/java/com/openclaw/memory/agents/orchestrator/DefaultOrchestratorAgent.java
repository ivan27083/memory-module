package com.openclaw.memory.agents.orchestrator;

import com.openclaw.memory.agents.BaseAgent;
import com.openclaw.memory.blackboard.*;
import com.openclaw.memory.event_store.Event;
import com.openclaw.memory.event_store.EventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Collectors;

/**
 * Orchestrator Agent Implementation
 * 
 * Основной оркестратор системы.
 * Отвечает за:
 * - Декомпозицию целей в задачи
 * - Назначение задач агентам
 * - Контроль порядка выполнения
 * - Валидацию гейтов завершения
 */
public class DefaultOrchestratorAgent implements OrchestratorAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultOrchestratorAgent.class);
    
    private final MemoryBlackboard blackboard;
    private final EventStore eventStore;
    private volatile AgentStatus status = AgentStatus.INITIALIZING;
    
    // Metrics
    private final AtomicLong plansCreated = new AtomicLong(0);
    private final AtomicLong tasksDecomposed = new AtomicLong(0);
    private final AtomicLong tasksAssigned = new AtomicLong(0);
    private final AtomicLong successfulDecompositions = new AtomicLong(0);
    private final AtomicLong failedDecompositions = new AtomicLong(0);
    private final CopyOnWriteArrayList<Long> decompositionTimes = new CopyOnWriteArrayList<>();
    
    private final Map<String, ExecutionPlan> activePlans = new ConcurrentHashMap<>();
    
    public DefaultOrchestratorAgent(MemoryBlackboard blackboard, EventStore eventStore) {
        this.blackboard = blackboard;
        this.eventStore = eventStore;
    }
    
    @Override
    public String getName() {
        return "ORCHESTRATOR";
    }
    
    @Override
    public String getDescription() {
        return "Декомпозирует все задачи системы, назначает агентам, " +
               "контролирует порядок выполнения и валидирует гейты завершения";
    }
    
    @Override
    public void initialize() {
        logger.info("Initializing Orchestrator Agent");
        status = AgentStatus.READY;
        recordEvent("orchestrator_initialized", "System orchestrator initialized successfully");
    }
    
    @Override
    public void shutdown() {
        logger.info("Shutting down Orchestrator Agent");
        status = AgentStatus.SHUTDOWN;
        recordEvent("orchestrator_shutdown", "System orchestrator shutdown");
    }
    
    @Override
    public boolean canHandle(Task task) {
        // Orchestrator может обработать задачи декомпозиции
        return task.getObjective().contains("decompose") || 
               task.getObjective().contains("orchestrate") ||
               task.getAgent().equals("ORCHESTRATOR");
    }
    
    @Override
    public CompletableFuture<List<Artifact>> executeTask(Task task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                status = AgentStatus.BUSY;
                task.markInProgress();
                
                List<Artifact> results = new ArrayList<>();
                
                // Получить содержимое из контекста задачи
                String objective = task.getObjective();
                List<String> inputArtifactIds = task.getInputs();
                
                // Декомпозировать задачу
                long startTime = System.currentTimeMillis();
                List<Task> decomposedTasks = decomposeObjective(objective, inputArtifactIds);
                long decompositionTime = System.currentTimeMillis() - startTime;
                decompositionTimes.add(decompositionTime);
                
                // Назначить задачи
                assignTasks(decomposedTasks);
                
                // Создать артефакт плана
                ExecutionPlan plan = new ExecutionPlan(
                        "PLAN-" + UUID.randomUUID(),
                        decomposedTasks,
                        0,
                        estimateCompletionTime(decomposedTasks)
                );
                
                activePlans.put(plan.planId, plan);
                plansCreated.incrementAndGet();
                
                // Создать артефакт
                Provenance provenance = new Provenance.Builder()
                        .addSourceEventId(task.getId())
                        .confidence(1.0f)
                        .putMetadata("decompositionTime", decompositionTime)
                        .build();
                
                Artifact planArtifact = new Artifact.Builder()
                        .artifactId(plan.planId)
                        .producedBy(getName())
                        .type(Artifact.ArtifactType.REPORT)
                        .provenance(provenance)
                        .content(Map.of(
                                "taskCount", decomposedTasks.size(),
                                "estimatedTime", plan.estimatedCompletionMs,
                                "tasks", decomposedTasks.stream().map(Task::getId).collect(Collectors.toList())
                        ))
                        .build();
                
                results.add(blackboard.publishArtifact(planArtifact));
                
                task.markComplete(List.of(plan.planId));
                status = AgentStatus.READY;
                successfulDecompositions.incrementAndGet();
                
                recordEvent("task_decomposition_complete", 
                        Map.of("taskCount", decomposedTasks.size(), "planId", plan.planId));
                
                return results;
                
            } catch (Exception e) {
                logger.error("Error executing orchestration task", e);
                task.markFailed("Orchestration failed: " + e.getMessage());
                status = AgentStatus.ERROR;
                failedDecompositions.incrementAndGet();
                recordEvent("task_decomposition_failed", Map.of("error", e.getMessage()));
                throw new RuntimeException(e);
            }
        });
    }
    
    @Override
    public void handleFailure(Task task, Exception error) {
        logger.error("Task {} failed: {}", task.getId(), error.getMessage());
        task.markFailed(error.getMessage());
        recordEvent("task_failed", Map.of(
                "taskId", task.getId(),
                "error", error.getMessage()
        ));
    }
    
    @Override
    public AgentStatus getStatus() {
        return status;
    }
    
    @Override
    public AgentMetrics getMetrics() {
        int total = (int) (successfulDecompositions.get() + failedDecompositions.get());
        double successRate = total == 0 ? 0 : 
                (double) successfulDecompositions.get() / total;
        
        long avgTime = decompositionTimes.isEmpty() ? 0 :
                decompositionTimes.stream().mapToLong(Long::longValue).sum() / 
                decompositionTimes.size();
        
        return new AgentMetrics(
                (int) successfulDecompositions.get(),
                (int) failedDecompositions.get(),
                avgTime,
                successRate,
                decompositionTimes.isEmpty() ? 0 : decompositionTimes.get(decompositionTimes.size() - 1)
        );
    }
    
    @Override
    public List<Task> decomposeObjective(String objective, List<String> context) {
        logger.info("Decomposing objective: {}", objective);
        
        List<Task> tasks = new ArrayList<>();
        
        // Простая декомпозиция на основе ключевых слов
        String[] parts = objective.split("\\s+");
        int taskNum = 1;
        
        for (String part : parts) {
            if (!part.isEmpty()) {
                Task subtask = new Task.Builder()
                        .id("TASK-" + UUID.randomUUID())
                        .agent("GENERIC")
                        .objective(part)
                        .inputs(context)
                        .acceptanceCriteria(true, true, true, true)
                        .build();
                
                tasks.add(subtask);
                taskNum++;
            }
        }
        
        tasksDecomposed.addAndGet(tasks.size());
        return tasks;
    }
    
    @Override
    public void assignTasks(List<Task> tasks) {
        logger.info("Assigning {} tasks to agents", tasks.size());
        
        for (Task task : tasks) {
            // Регистрировать задачу в blackboard
            blackboard.registerTask(task);
            tasksAssigned.incrementAndGet();
        }
    }
    
    @Override
    public boolean canExecuteTask(Task task) {
        // Проверить, доступны ли зависимости
        List<Task> dependencies = getTaskDependencies(task);
        return dependencies.stream()
                .allMatch(t -> t.getStatus() == Task.TaskStatus.DONE);
    }
    
    @Override
    public List<Task> getTaskDependencies(Task task) {
        return task.getInputs().stream()
                .map(blackboard::getTask)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    @Override
    public boolean areDependenciesSatisfied(Task task) {
        return getTaskDependencies(task).stream()
                .allMatch(t -> t.getStatus() == Task.TaskStatus.DONE);
    }
    
    @Override
    public List<Task> getOptimalExecutionOrder(List<Task> tasks) {
        // Топологическая сортировка задач
        // Упрощенная реализация - возвращаем как есть
        return new ArrayList<>(tasks);
    }
    
    @Override
    public boolean validateSystemState() {
        logger.info("Validating system state");
        
        // Проверить инварианты
        List<Task> failedTasks = blackboard.getTasksByStatus(Task.TaskStatus.FAILED);
        
        if (!failedTasks.isEmpty()) {
            logger.warn("Found {} failed tasks", failedTasks.size());
            return false;
        }
        
        return true;
    }
    
    @Override
    public ExecutionPlan getCurrentExecutionPlan() {
        return activePlans.values().stream()
                .findFirst()
                .orElse(null);
    }
    
    @Override
    public void replan(String failureReason) {
        logger.warn("Replanning due to: {}", failureReason);
        
        List<Task> failedTasks = blackboard.getTasksByStatus(Task.TaskStatus.FAILED);
        logger.info("Replan required for {} failed tasks", failedTasks.size());
        
        // Переназначить задачи с другим агентом
        for (Task task : failedTasks) {
            Task replanTask = new Task.Builder()
                    .id("TASK-REPLAN-" + UUID.randomUUID())
                    .agent("DIFFERENT_AGENT")
                    .objective(task.getObjective())
                    .inputs(task.getInputs())
                    .acceptanceCriteria(true, true, true, true)
                    .build();
            
            blackboard.registerTask(replanTask);
        }
    }
    
    @Override
    public OrchestratorStats getOrchestratorStats() {
        long avgTime = decompositionTimes.isEmpty() ? 0 :
                decompositionTimes.stream().mapToLong(Long::longValue).sum() / 
                decompositionTimes.size();
        
        long total = successfulDecompositions.get() + failedDecompositions.get();
        double successRate = total == 0 ? 0 : 
                (double) successfulDecompositions.get() / total;
        
        return new OrchestratorStats(
                plansCreated.get(),
                tasksDecomposed.get(),
                tasksAssigned.get(),
                successRate,
                avgTime
        );
    }
    
    // ================ HELPERS ================
    
    private double estimateCompletionTime(List<Task> tasks) {
        // Упрощенная оценка: 100ms per task
        return tasks.size() * 100.0;
    }
    
    private void recordEvent(String eventType, Object details) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("details", details.toString());
            
            Event event = new Event.Builder()
                    .eventId("EVT-ORCH-" + UUID.randomUUID())
                    .sourceAgent(getName())
                    .eventType(Event.EventType.SYSTEM_EVENT)
                    .payload(payload)
                    .build();
            
            eventStore.appendEvent(event);
        } catch (Exception e) {
            logger.warn("Failed to record event: {}", e.getMessage());
        }
    }
    
    private void recordEvent(String eventType, Map<String, Object> details) {
        try {
            Event event = new Event.Builder()
                    .eventId("EVT-ORCH-" + UUID.randomUUID())
                    .sourceAgent(getName())
                    .eventType(Event.EventType.SYSTEM_EVENT)
                    .payload(details)
                    .build();
            
            eventStore.appendEvent(event);
        } catch (Exception e) {
            logger.warn("Failed to record event: {}", e.getMessage());
        }
    }
}
