package com.openclaw.memory.blackboard;

import java.time.Instant;
import java.util.*;

/**
 * Task Contract - описание работ, назначаемых агентам.
 * Каждая задача имеет строгие критерии приемки.
 */
public class Task {
    
    public enum TaskStatus {
        PENDING,
        IN_PROGRESS,
        BLOCKED,
        DONE,
        FAILED
    }
    
    private final String id;
    private final String agent;
    private final String objective;
    private final List<String> inputs;
    private final List<String> expectedOutputs;
    private final AcceptanceCriteria acceptanceCriteria;
    private volatile TaskStatus status;
    private final Instant createdAt;
    private volatile Instant startedAt;
    private volatile Instant completedAt;
    private volatile String failureReason;
    private volatile List<String> actualOutputs;
    
    public static class AcceptanceCriteria {
        public final boolean requiresDeterministic;
        public final boolean requiresReproducible;
        public final boolean requiresProvenanceValid;
        public final boolean requiresTest;
        public final List<String> customCriteria;
        
        public AcceptanceCriteria(boolean deterministic, boolean reproducible, 
                                boolean provenanceValid, boolean test, 
                                List<String> custom) {
            this.requiresDeterministic = deterministic;
            this.requiresReproducible = reproducible;
            this.requiresProvenanceValid = provenanceValid;
            this.requiresTest = test;
            this.customCriteria = custom != null ? Collections.unmodifiableList(custom) : List.of();
        }
    }
    
    public Task(Builder builder) {
        this.id = builder.id;
        this.agent = builder.agent;
        this.objective = builder.objective;
        this.inputs = Collections.unmodifiableList(builder.inputs);
        this.expectedOutputs = Collections.unmodifiableList(builder.expectedOutputs);
        this.acceptanceCriteria = builder.acceptanceCriteria;
        this.status = TaskStatus.PENDING;
        this.createdAt = Instant.now();
        this.actualOutputs = new ArrayList<>();
    }
    
    // Getters
    public String getId() { return id; }
    public String getAgent() { return agent; }
    public String getObjective() { return objective; }
    public List<String> getInputs() { return inputs; }
    public List<String> getExpectedOutputs() { return expectedOutputs; }
    public AcceptanceCriteria getAcceptanceCriteria() { return acceptanceCriteria; }
    public TaskStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getFailureReason() { return failureReason; }
    public List<String> getActualOutputs() { return new ArrayList<>(actualOutputs); }
    
    // State transition methods
    public synchronized void markInProgress() {
        if (status != TaskStatus.PENDING) {
            throw new IllegalStateException("Task cannot be marked in progress from state: " + status);
        }
        this.status = TaskStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
    }
    
    public synchronized void markBlocked(String reason) {
        if (status != TaskStatus.IN_PROGRESS) {
            throw new IllegalStateException("Task cannot be blocked from state: " + status);
        }
        this.status = TaskStatus.BLOCKED;
        this.failureReason = reason;
    }
    
    public synchronized void markComplete(List<String> outputs) {
        if (status != TaskStatus.IN_PROGRESS && status != TaskStatus.BLOCKED) {
            throw new IllegalStateException("Task cannot be completed from state: " + status);
        }
        this.status = TaskStatus.DONE;
        this.actualOutputs = new ArrayList<>(outputs);
        this.completedAt = Instant.now();
    }
    
    public synchronized void markFailed(String reason) {
        if (status == TaskStatus.DONE) {
            throw new IllegalStateException("Completed task cannot be marked as failed");
        }
        this.status = TaskStatus.FAILED;
        this.failureReason = reason;
        this.completedAt = Instant.now();
    }
    
    public long getExecutionTimeMs() {
        if (startedAt == null) return 0;
        Instant end = completedAt != null ? completedAt : Instant.now();
        return java.time.Duration.between(startedAt, end).toMillis();
    }
    
    public static class Builder {
        private String id;
        private String agent;
        private String objective;
        private List<String> inputs = new ArrayList<>();
        private List<String> expectedOutputs = new ArrayList<>();
        private AcceptanceCriteria acceptanceCriteria;
        
        public Builder id(String id) { this.id = id; return this; }
        public Builder agent(String agent) { this.agent = agent; return this; }
        public Builder objective(String obj) { this.objective = obj; return this; }
        public Builder inputs(List<String> inp) { this.inputs = new ArrayList<>(inp); return this; }
        public Builder addInput(String input) { this.inputs.add(input); return this; }
        public Builder expectedOutputs(List<String> out) { this.expectedOutputs = new ArrayList<>(out); return this; }
        public Builder addExpectedOutput(String output) { this.expectedOutputs.add(output); return this; }
        
        public Builder acceptanceCriteria(boolean deterministic, boolean reproducible, 
                                         boolean provenanceValid, boolean test) {
            this.acceptanceCriteria = new AcceptanceCriteria(deterministic, reproducible, 
                                                             provenanceValid, test, null);
            return this;
        }
        
        public Builder acceptanceCriteria(AcceptanceCriteria criteria) {
            this.acceptanceCriteria = criteria;
            return this;
        }
        
        public Task build() {
            if (id == null || agent == null || objective == null) {
                throw new IllegalArgumentException("ID, agent, and objective are required");
            }
            if (acceptanceCriteria == null) {
                this.acceptanceCriteria = new AcceptanceCriteria(true, true, true, true, null);
            }
            return new Task(this);
        }
    }
    
    @Override
    public String toString() {
        return "Task{" +
                "id='" + id + '\'' +
                ", agent='" + agent + '\'' +
                ", status=" + status +
                ", objective='" + objective + '\'' +
                '}';
    }
}
