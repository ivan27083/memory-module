package com.openclaw.memory.blackboard;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Memory Blackboard - центральная шина коммуникации между агентами.
 * Содержит задачи, артефакты, обновления графов, следы извлечения и отчеты о конфликтах.
 * Потокобезопасна.
 */
public class MemoryBlackboard {
    
    private static final MemoryBlackboard INSTANCE = new MemoryBlackboard();
    
    // Task management
    private final ConcurrentHashMap<String, Task> tasks = new ConcurrentHashMap<>();
    private final BlockingQueue<Task> taskQueue = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<String, List<Task>> tasksByAgent = new ConcurrentHashMap<>();
    
    // Artifact storage
    private final ConcurrentHashMap<String, Artifact> artifacts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<String>> artifactsByProducer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<String>> artifactsByType = new ConcurrentHashMap<>();
    
    // Listeners for artifact updates
    private final List<ArtifactListener> artifactListeners = new CopyOnWriteArrayList<>();
    
    // Conflict and state tracking
    private final List<ConflictReport> conflictReports = new CopyOnWriteArrayList<>();
    private final List<StateSnapshot> stateHistory = new CopyOnWriteArrayList<>();
    
    // Metrics
    private volatile long totalTasksProcessed = 0;
    private volatile long totalArtifactsCreated = 0;
    
    public MemoryBlackboard() {}
    
    public static MemoryBlackboard getInstance() {
        return INSTANCE;
    }
    
    // ================ TASK MANAGEMENT ================
    
    /**
     * Регистрирует новую задачу в очереди
     */
    public Task registerTask(Task task) {
        if (tasks.containsKey(task.getId())) {
            throw new IllegalArgumentException("Task with ID already exists: " + task.getId());
        }
        tasks.put(task.getId(), task);
        taskQueue.offer(task);
        tasksByAgent.computeIfAbsent(task.getAgent(), k -> new CopyOnWriteArrayList<>()).add(task);
        return task;
    }
    
    /**
     * Получить следующую задачу из очереди (блокирующая операция)
     */
    public Task getNextTask(long timeoutMs) throws InterruptedException {
        return taskQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Получить задачу по ID
     */
    public Task getTask(String taskId) {
        return tasks.get(taskId);
    }
    
    /**
     * Получить все задачи для агента
     */
    public List<Task> getTasksForAgent(String agentName) {
        List<Task> result = tasksByAgent.get(agentName);
        return result != null ? new ArrayList<>(result) : List.of();
    }
    
    /**
     * Получить все задачи определенного статуса
     */
    public List<Task> getTasksByStatus(Task.TaskStatus status) {
        return tasks.values().stream()
                .filter(t -> t.getStatus() == status)
                .collect(Collectors.toList());
    }
    
    // ================ ARTIFACT MANAGEMENT ================
    
    /**
     * Публикует артефакт на blackboard. Это операция APPEND-ONLY.
     * Все артефакты неизменяемы после публикации.
     */
    public synchronized Artifact publishArtifact(Artifact artifact) {
        if (artifacts.containsKey(artifact.getArtifactId())) {
            throw new IllegalStateException("Artifact already exists (immutable): " + artifact.getArtifactId());
        }
        
        artifacts.put(artifact.getArtifactId(), artifact);
        artifactsByProducer
                .computeIfAbsent(artifact.getProducedBy(), k -> new CopyOnWriteArrayList<>())
                .add(artifact.getArtifactId());
        
        artifactsByType
                .computeIfAbsent(artifact.getType().name(), k -> new CopyOnWriteArrayList<>())
                .add(artifact.getArtifactId());
        
        totalArtifactsCreated++;
        
        // Notify listeners
        for (ArtifactListener listener : artifactListeners) {
            listener.onArtifactPublished(artifact);
        }
        
        return artifact;
    }
    
    /**
     * Получить артефакт по ID
     */
    public Optional<Artifact> getArtifact(String artifactId) {
        return Optional.ofNullable(artifacts.get(artifactId));
    }

    /**
     * Backwards-compatible alias for publishArtifact.
     */
    public Artifact storeArtifact(Artifact artifact) {
        return publishArtifact(artifact);
    }

    /**
     * Applies an in-place metadata/provenance update to an artifact.
     */
    public synchronized void updateArtifact(String artifactId, java.util.function.Function<Artifact, ?> updater) {
        Artifact artifact = artifacts.get(artifactId);
        if (artifact == null) {
            throw new NoSuchElementException("Artifact not found: " + artifactId);
        }
        updater.apply(artifact);
    }
    
    /**
     * Получить все артефакты определенного типа
     */
    public List<Artifact> getArtifactsByType(Artifact.ArtifactType type) {
        List<String> ids = artifactsByType.get(type.name());
        if (ids == null) return List.of();
        return ids.stream()
                .map(artifacts::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * Получить все артефакты от определенного агента
     */
    public List<Artifact> getArtifactsByProducer(String producerAgent) {
        List<String> ids = artifactsByProducer.get(producerAgent);
        if (ids == null) return List.of();
        return ids.stream()
                .map(artifacts::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * Получить артефакты, на которых зависит данный артефакт
     */
    public List<Artifact> getDependencies(String artifactId) {
        Artifact artifact = artifacts.get(artifactId);
        if (artifact == null) return List.of();
        return artifact.getDependsOn().stream()
                .map(artifacts::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * Получить артефакты, которые зависят от данного
     */
    public List<Artifact> getDependents(String artifactId) {
        return artifacts.values().stream()
                .filter(a -> a.getDependsOn().contains(artifactId))
                .collect(Collectors.toList());
    }
    
    // ================ CONFLICT & STATE MANAGEMENT ================
    
    /**
     * Регистрирует отчет о конфликте
     */
    public void reportConflict(ConflictReport report) {
        conflictReports.add(report);
        for (ArtifactListener listener : artifactListeners) {
            listener.onConflictDetected(report);
        }
    }
    
    /**
     * Получить все неразрешенные конфликты
     */
    public List<ConflictReport> getUnresolvedConflicts() {
        return conflictReports.stream()
                .filter(c -> !c.isResolved())
                .collect(Collectors.toList());
    }
    
    /**
     * Создать снимок текущего состояния
     */
    public StateSnapshot createStateSnapshot() {
        StateSnapshot snapshot = new StateSnapshot(
                artifacts.size(),
                tasks.size(),
                conflictReports.size(),
                getTasksByStatus(Task.TaskStatus.IN_PROGRESS).size(),
                getTasksByStatus(Task.TaskStatus.FAILED).size()
        );
        stateHistory.add(snapshot);
        return snapshot;
    }
    
    // ================ LISTENERS ================
    
    public void addArtifactListener(ArtifactListener listener) {
        artifactListeners.add(listener);
    }
    
    public void removeArtifactListener(ArtifactListener listener) {
        artifactListeners.remove(listener);
    }
    
    // ================ METRICS ================
    
    public long getTotalTasksProcessed() {
        return totalTasksProcessed;
    }
    
    public long getTotalArtifactsCreated() {
        return totalArtifactsCreated;
    }
    
    public int getCurrentArtifactCount() {
        return artifacts.size();
    }
    
    public int getQueuedTaskCount() {
        return taskQueue.size();
    }
    
    public double getConflictRate() {
        if (totalArtifactsCreated == 0) return 0;
        return (double) conflictReports.size() / totalArtifactsCreated;
    }
    
    // ================ CLEARING (for testing) ================
    
    public void clear() {
        tasks.clear();
        artifacts.clear();
        taskQueue.clear();
        tasksByAgent.clear();
        artifactsByProducer.clear();
        artifactsByType.clear();
        conflictReports.clear();
        stateHistory.clear();
    }
    
    // ================ LISTENER INTERFACE ================
    
    public interface ArtifactListener {
        void onArtifactPublished(Artifact artifact);
        void onConflictDetected(ConflictReport report);
    }
    
    // ================ SUPPORTING CLASSES ================
    
    public static class ConflictReport {
        public final String conflictId;
        public final List<String> conflictingArtifactIds;
        public final String description;
        public final ConflictType type;
        public volatile boolean resolved;
        public volatile String resolution;
        
        public enum ConflictType {
            CONTRADICTION,
            SUPERSESSION,
            TEMPORAL_ANOMALY,
            CONFIDENCE_MISMATCH
        }
        
        public ConflictReport(String id, List<String> artifacts, String desc, ConflictType type) {
            this.conflictId = id;
            this.conflictingArtifactIds = Collections.unmodifiableList(artifacts);
            this.description = desc;
            this.type = type;
            this.resolved = false;
        }
        
        public boolean isResolved() { return resolved; }
    }
    
    public static class StateSnapshot {
        public final int artifactCount;
        public final int taskCount;
        public final int conflictCount;
        public final int inProgressCount;
        public final int failedCount;
        public final java.time.Instant timestamp;
        
        public StateSnapshot(int artifacts, int tasks, int conflicts, 
                           int inProgress, int failed) {
            this.artifactCount = artifacts;
            this.taskCount = tasks;
            this.conflictCount = conflicts;
            this.inProgressCount = inProgress;
            this.failedCount = failed;
            this.timestamp = java.time.Instant.now();
        }
    }
    
    @Override
    public String toString() {
        return "MemoryBlackboard{" +
                "artifacts=" + artifacts.size() +
                ", tasks=" + tasks.size() +
                ", queued=" + taskQueue.size() +
                ", conflicts=" + conflictReports.size() +
                '}';
    }
}
