package com.openclaw.memory.event_store;

import java.time.Instant;
import java.util.*;

/**
 * Event - базовый класс для всех событий в системе.
 * Все события неизменяемы и содержат провенанс.
 */
public class Event {
    
    public enum EventType {
        MEMORY_RECORDED,
        MEMORY_UPDATED,
        MEMORY_RETRIEVED,
        MEMORY_FORGOTTEN,
        CONFLICT_DETECTED,
        CONFLICT_RESOLVED,
        BELIEF_UPDATED,
        GRAPH_UPDATED,
        INDEX_UPDATED,
        SYSTEM_EVENT
    }
    
    private final String eventId;
    private final String sourceAgent;
    private final EventType eventType;
    private final Instant timestamp;
    private final Map<String, Object> payload;
    private final String correlationId;
    private final List<String> causedBy;
    private final long sequenceNumber;
    
    public Event(Builder builder) {
        this.eventId = builder.eventId;
        this.sourceAgent = builder.sourceAgent;
        this.eventType = builder.eventType;
        this.timestamp = builder.timestamp;
        this.payload = Collections.unmodifiableMap(new HashMap<>(builder.payload));
        this.correlationId = builder.correlationId;
        this.causedBy = Collections.unmodifiableList(new ArrayList<>(builder.causedBy));
        this.sequenceNumber = builder.sequenceNumber;
    }
    
    public String getEventId() { return eventId; }
    public String getSourceAgent() { return sourceAgent; }
    public EventType getEventType() { return eventType; }
    public Instant getTimestamp() { return timestamp; }
    public Map<String, Object> getPayload() { return payload; }
    public String getCorrelationId() { return correlationId; }
    public List<String> getCausedBy() { return causedBy; }
    public long getSequenceNumber() { return sequenceNumber; }
    
    public static class Builder {
        private String eventId;
        private String sourceAgent;
        private EventType eventType;
        private Instant timestamp = Instant.now();
        private Map<String, Object> payload = new HashMap<>();
        private String correlationId;
        private List<String> causedBy = new ArrayList<>();
        private long sequenceNumber;
        
        public Builder eventId(String id) { this.eventId = id; return this; }
        public Builder sourceAgent(String agent) { this.sourceAgent = agent; return this; }
        public Builder eventType(EventType type) { this.eventType = type; return this; }
        public Builder timestamp(Instant ts) { this.timestamp = ts; return this; }
        public Builder payload(Map<String, Object> data) { this.payload = new HashMap<>(data); return this; }
        public Builder addPayloadEntry(String key, Object value) { this.payload.put(key, value); return this; }
        public Builder correlationId(String id) { this.correlationId = id; return this; }
        public Builder causedBy(List<String> causes) { this.causedBy = new ArrayList<>(causes); return this; }
        public Builder addCause(String eventId) { this.causedBy.add(eventId); return this; }
        public Builder sequenceNumber(long seq) { this.sequenceNumber = seq; return this; }
        
        public Event build() {
            if (eventId == null || sourceAgent == null || eventType == null) {
                throw new IllegalArgumentException("Event ID, source agent, and type are required");
            }
            return new Event(this);
        }
    }
    
    @Override
    public String toString() {
        return "Event{" +
                "id='" + eventId + '\'' +
                ", type=" + eventType +
                ", agent='" + sourceAgent + '\'' +
                ", timestamp=" + timestamp +
                ", seq=" + sequenceNumber +
                '}';
    }
}
