package com.openclaw.memory.agents.event_store;

import com.openclaw.memory.agents.BaseAgent;
import com.openclaw.memory.blackboard.*;
import com.openclaw.memory.event_store.Event;
import com.openclaw.memory.event_store.EventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Event Store Agent Implementation
 * 
 * Управляет неизменяемым логом событий.
 * Отвечает за:
 * - Запись событий (append-only)
 * - Обеспечение целостности провенанса
 * - Управление DuckDB + Parquet хранилищем
 */
public class EventStoreAgentImpl implements BaseAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(EventStoreAgentImpl.class);
    
    private final EventStore eventStore;
    private final MemoryBlackboard blackboard;
    private volatile AgentStatus status = AgentStatus.INITIALIZING;
    
    // Metrics
    private final AtomicLong eventsRecorded = new AtomicLong(0);
    private final AtomicLong eventsFailed = new AtomicLong(0);
    private final AtomicLong queriesProcessed = new AtomicLong(0);
    private final CopyOnWriteArrayList<Long> recordingTimes = new CopyOnWriteArrayList<>();
    
    public EventStoreAgentImpl(EventStore eventStore, MemoryBlackboard blackboard) {
        this.eventStore = eventStore;
        this.blackboard = blackboard;
    }
    
    @Override
    public String getName() {
        return "EVENT_STORE";
    }
    
    @Override
    public String getDescription() {
        return "Управляет неизменяемым логом событий. Гарантирует целостность провенанса " +
               "и управляет DuckDB + Parquet хранилищем";
    }
    
    @Override
    public void initialize() {
        logger.info("Initializing Event Store Agent");
        status = AgentStatus.READY;
        logger.info("Event Store Agent ready. Current event count: {}", eventStore.getEventCount());
    }
    
    @Override
    public void shutdown() {
        logger.info("Shutting down Event Store Agent");
        status = AgentStatus.SHUTDOWN;
    }
    
    @Override
    public boolean canHandle(Task task) {
        return task.getObjective().contains("event") ||
               task.getAgent().equals("EVENT_STORE");
    }
    
    @Override
    public CompletableFuture<List<Artifact>> executeTask(Task task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                status = AgentStatus.BUSY;
                task.markInProgress();
                
                List<Artifact> results = new ArrayList<>();
                String objective = task.getObjective();
                
                if (objective.contains("record")) {
                    // Записать события
                    results.addAll(handleRecordEvents(task));
                } else if (objective.contains("query") || objective.contains("retrieve")) {
                    // Запросить события
                    results.addAll(handleQueryEvents(task));
                } else if (objective.contains("export")) {
                    // Экспортировать события
                    results.addAll(handleExportEvents(task));
                }
                
                task.markComplete(results.stream().map(Artifact::getArtifactId).collect(java.util.stream.Collectors.toList()));
                status = AgentStatus.READY;
                
                return results;
                
            } catch (Exception e) {
                logger.error("Error in Event Store Agent", e);
                task.markFailed(e.getMessage());
                status = AgentStatus.ERROR;
                eventsFailed.incrementAndGet();
                throw new RuntimeException(e);
            }
        });
    }
    
    @Override
    public void handleFailure(Task task, Exception error) {
        logger.error("Event Store task {} failed: {}", task.getId(), error.getMessage());
        eventsFailed.incrementAndGet();
    }
    
    @Override
    public AgentStatus getStatus() {
        return status;
    }
    
    @Override
    public AgentMetrics getMetrics() {
        int total = (int) (eventsRecorded.get() + eventsFailed.get());
        double successRate = total == 0 ? 0 : 
                (double) eventsRecorded.get() / total;
        
        long avgTime = recordingTimes.isEmpty() ? 0 :
                recordingTimes.stream().mapToLong(Long::longValue).sum() / 
                recordingTimes.size();
        
        return new AgentMetrics(
                (int) eventsRecorded.get(),
                (int) eventsFailed.get(),
                avgTime,
                successRate,
                recordingTimes.isEmpty() ? 0 : recordingTimes.get(recordingTimes.size() - 1)
        );
    }
    
    // ================ EVENT OPERATIONS ================
    
    /**
     * Обработать задачу записи событий
     */
    private List<Artifact> handleRecordEvents(Task task) {
        logger.info("Recording events for task: {}", task.getId());
        List<Artifact> results = new ArrayList<>();
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Получить события из входных артефактов
            List<Event> eventsToRecord = new ArrayList<>();
            for (String artifactId : task.getInputs()) {
                Artifact artifact = blackboard.getArtifact(artifactId);
                if (artifact != null) {
                    Event event = new Event.Builder()
                            .eventId("EVT-" + UUID.randomUUID())
                            .sourceAgent(artifact.getProducedBy())
                            .eventType(Event.EventType.MEMORY_RECORDED)
                            .payload(new HashMap<>(artifact.getContent()))
                            .build();
                    eventsToRecord.add(event);
                }
            }
            
            // Записать события в Event Store
            for (Event event : eventsToRecord) {
                eventStore.appendEvent(event);
            }
            
            long recordingTime = System.currentTimeMillis() - startTime;
            recordingTimes.add(recordingTime);
            eventsRecorded.addAndGet(eventsToRecord.size());
            
            // Создать артефакт результата
            Provenance provenance = new Provenance.Builder()
                    .addSourceEventId(task.getId())
                    .confidence(1.0f)
                    .putMetadata("eventCount", eventsToRecord.size())
                    .putMetadata("recordingTime", recordingTime)
                    .build();
            
            Artifact resultArtifact = new Artifact.Builder()
                    .artifactId("EVENT-RECORD-" + UUID.randomUUID())
                    .producedBy(getName())
                    .type(Artifact.ArtifactType.EVENT)
                    .provenance(provenance)
                    .content(Map.of(
                            "recordedEvents", eventsToRecord.size(),
                            "totalEvents", eventStore.getEventCount(),
                            "timestamp", Instant.now().toString()
                    ))
                    .build();
            
            results.add(blackboard.publishArtifact(resultArtifact));
            
        } catch (Exception e) {
            logger.error("Failed to record events", e);
            eventsFailed.incrementAndGet();
            throw new RuntimeException(e);
        }
        
        return results;
    }
    
    /**
     * Обработать задачу запроса событий
     */
    private List<Artifact> handleQueryEvents(Task task) {
        logger.info("Querying events for task: {}", task.getId());
        List<Artifact> results = new ArrayList<>();
        
        try {
            queriesProcessed.incrementAndGet();
            
            // Получить все события (упрощенная реализация)
            List<Event> events = eventStore.getAllEvents();
            
            // Создать артефакт результата
            Provenance provenance = new Provenance.Builder()
                    .addSourceEventId(task.getId())
                    .confidence(1.0f)
                    .putMetadata("eventCount", events.size())
                    .build();
            
            List<String> eventIds = events.stream()
                    .map(Event::getEventId)
                    .collect(java.util.stream.Collectors.toList());
            
            Artifact resultArtifact = new Artifact.Builder()
                    .artifactId("EVENT-QUERY-" + UUID.randomUUID())
                    .producedBy(getName())
                    .type(Artifact.ArtifactType.MEMORY)
                    .provenance(provenance)
                    .content(Map.of(
                            "eventIds", eventIds,
                            "totalCount", events.size(),
                            "timestamp", Instant.now().toString()
                    ))
                    .build();
            
            results.add(blackboard.publishArtifact(resultArtifact));
            
        } catch (Exception e) {
            logger.error("Failed to query events", e);
            eventsFailed.incrementAndGet();
            throw new RuntimeException(e);
        }
        
        return results;
    }
    
    /**
     * Обработать задачу экспорта событий
     */
    private List<Artifact> handleExportEvents(Task task) {
        logger.info("Exporting events for task: {}", task.getId());
        List<Artifact> results = new ArrayList<>();
        
        try {
            // Экспортировать в Parquet (if implemented)
            String exportPath = task.getObjective().contains("path:") ? 
                    task.getObjective().split("path:")[1].trim() : 
                    "/tmp/events.parquet";
            
            String result = eventStore.exportToParquet(exportPath);
            
            Provenance provenance = new Provenance.Builder()
                    .addSourceEventId(task.getId())
                    .confidence(1.0f)
                    .putMetadata("exportPath", exportPath)
                    .build();
            
            Artifact resultArtifact = new Artifact.Builder()
                    .artifactId("EVENT-EXPORT-" + UUID.randomUUID())
                    .producedBy(getName())
                    .type(Artifact.ArtifactType.REPORT)
                    .provenance(provenance)
                    .content(Map.of(
                            "exportResult", result,
                            "path", exportPath,
                            "timestamp", Instant.now().toString()
                    ))
                    .build();
            
            results.add(blackboard.publishArtifact(resultArtifact));
            
        } catch (Exception e) {
            logger.error("Failed to export events", e);
            eventsFailed.incrementAndGet();
            throw new RuntimeException(e);
        }
        
        return results;
    }
    
    // ================ STATISTICS ================
    
    /**
     * Получить статистику Event Store
     */
    public EventStore.EventStoreStats getEventStoreStats() {
        return eventStore.getStats();
    }
    
    /**
     * Получить количество записанных событий
     */
    public long getEventsRecorded() {
        return eventsRecorded.get();
    }
    
    /**
     * Получить количество ошибок
     */
    public long getEventsFailed() {
        return eventsFailed.get();
    }
    
    /**
     * Получить количество обработанных запросов
     */
    public long getQueriesProcessed() {
        return queriesProcessed.get();
    }
}
