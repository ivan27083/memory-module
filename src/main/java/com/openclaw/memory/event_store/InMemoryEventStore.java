package com.openclaw.memory.event_store;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * InMemoryEventStore - базовая реализация хранилища событий в памяти.
 * Потокобезопасна. В будущем будет заменена на DuckDB+Parquet.
 */
public class InMemoryEventStore implements EventStore {
    
    private final ConcurrentHashMap<String, Event> events = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Event> eventLog = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<Event.EventType, List<String>> eventsByType = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<String>> eventsByAgent = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<String>> eventsByCorrelation = new ConcurrentHashMap<>();
    private volatile long sequenceCounter = 0;
    
    @Override
    public synchronized void appendEvent(Event event) {
        if (events.containsKey(event.getEventId())) {
            throw new IllegalStateException("Event already exists (immutable): " + event.getEventId());
        }
        
        events.put(event.getEventId(), event);
        eventLog.offer(event);
        
        // Index by type
        eventsByType.computeIfAbsent(event.getEventType(), k -> new CopyOnWriteArrayList<>())
                .add(event.getEventId());
        
        // Index by agent
        eventsByAgent.computeIfAbsent(event.getSourceAgent(), k -> new CopyOnWriteArrayList<>())
                .add(event.getEventId());
        
        // Index by correlation
        if (event.getCorrelationId() != null) {
            eventsByCorrelation.computeIfAbsent(event.getCorrelationId(), k -> new CopyOnWriteArrayList<>())
                    .add(event.getEventId());
        }
        
        sequenceCounter++;
    }
    
    @Override
    public Event getEvent(String eventId) {
        return events.get(eventId);
    }
    
    @Override
    public List<Event> getAllEvents() {
        return new ArrayList<>(eventLog);
    }
    
    @Override
    public List<Event> getEventsByTimeRange(Instant from, Instant to) {
        return eventLog.stream()
                .filter(e -> !e.getTimestamp().isBefore(from) && !e.getTimestamp().isAfter(to))
                .collect(Collectors.toList());
    }
    
    @Override
    public List<Event> getEventsByType(Event.EventType type) {
        List<String> ids = eventsByType.get(type);
        if (ids == null) return List.of();
        return ids.stream()
                .map(events::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<Event> getEventsByAgent(String agentName) {
        List<String> ids = eventsByAgent.get(agentName);
        if (ids == null) return List.of();
        return ids.stream()
                .map(events::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<Event> getEventsByCorrelation(String correlationId) {
        List<String> ids = eventsByCorrelation.get(correlationId);
        if (ids == null) return List.of();
        return ids.stream()
                .map(events::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<Event> getEventsCausedBy(String eventId) {
        return eventLog.stream()
                .filter(e -> e.getCausedBy().contains(eventId))
                .collect(Collectors.toList());
    }
    
    @Override
    public long getEventCount() {
        return events.size();
    }
    
    @Override
    public Event getLastEvent() {
        List<Event> allEvents = new ArrayList<>(eventLog);
        return allEvents.isEmpty() ? null : allEvents.get(allEvents.size() - 1);
    }
    
    @Override
    public List<Event> getEventsPaged(int pageNumber, int pageSize) {
        List<Event> allEvents = new ArrayList<>(eventLog);
        int fromIndex = pageNumber * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, allEvents.size());
        
        if (fromIndex >= allEvents.size()) {
            return List.of();
        }
        
        return allEvents.subList(fromIndex, toIndex);
    }
    
    @Override
    public String exportToParquet(String outputPath) {
        // TODO: Реализовать экспорт в Parquet
        return "Export to " + outputPath + " not implemented yet";
    }
    
    @Override
    public EventStoreStats getStats() {
        Map<Event.EventType, Long> typeDistribution = new HashMap<>();
        for (Event.EventType type : Event.EventType.values()) {
            typeDistribution.put(type, (long) getEventsByType(type).size());
        }
        
        Map<String, Long> agentDistribution = new HashMap<>();
        eventsByAgent.forEach((agent, ids) -> agentDistribution.put(agent, (long) ids.size()));
        
        Instant oldest = eventLog.stream()
                .map(Event::getTimestamp)
                .min(Instant::compareTo)
                .orElse(null);
        
        Instant newest = eventLog.stream()
                .map(Event::getTimestamp)
                .max(Instant::compareTo)
                .orElse(null);
        
        // Rough estimate of storage size
        long storageBytes = events.size() * 1024; // ~1KB per event estimate
        
        return new EventStoreStats(
                events.size(),
                typeDistribution,
                agentDistribution,
                oldest,
                newest,
                storageBytes
        );
    }
    
    /**
     * Очистить хранилище (для тестирования)
     */
    public void clear() {
        events.clear();
        eventLog.clear();
        eventsByType.clear();
        eventsByAgent.clear();
        eventsByCorrelation.clear();
        sequenceCounter = 0;
    }
}
