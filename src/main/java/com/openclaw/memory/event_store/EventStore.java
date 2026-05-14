package com.openclaw.memory.event_store;

import java.time.Instant;
import java.util.*;

/**
 * EventStore Interface - неизменяемый лог событий.
 * Строго append-only. Никаких мутаций или удалений.
 */
public interface EventStore {
    
    /**
     * Добавить событие в хранилище (append-only)
     */
    void appendEvent(Event event);
    
    /**
     * Получить событие по ID
     */
    Event getEvent(String eventId);
    
    /**
     * Получить все события в порядке последовательности
     */
    List<Event> getAllEvents();
    
    /**
     * Получить события за временной интервал
     */
    List<Event> getEventsByTimeRange(Instant from, Instant to);
    
    /**
     * Получить события определенного типа
     */
    List<Event> getEventsByType(Event.EventType type);
    
    /**
     * Получить события от определенного агента
     */
    List<Event> getEventsByAgent(String agentName);
    
    /**
     * Получить события по correlation ID (логически связанные)
     */
    List<Event> getEventsByCorrelation(String correlationId);
    
    /**
     * Получить события, вызванные определенным событием
     */
    List<Event> getEventsCausedBy(String eventId);
    
    /**
     * Получить общее количество событий
     */
    long getEventCount();
    
    /**
     * Получить последнее событие
     */
    Event getLastEvent();
    
    /**
     * Получить события с пагинацией
     */
    List<Event> getEventsPaged(int pageNumber, int pageSize);
    
    /**
     * Экспортировать события в Parquet (для аналитики)
     */
    String exportToParquet(String outputPath);
    
    /**
     * Получить статистику хранилища
     */
    EventStoreStats getStats();
    
    class EventStoreStats {
        public final long totalEvents;
        public final Map<Event.EventType, Long> eventTypeDistribution;
        public final Map<String, Long> eventsByAgent;
        public final Instant oldestEventTime;
        public final Instant newestEventTime;
        public final long storageBytes;
        
        public EventStoreStats(long total, Map<Event.EventType, Long> distribution,
                             Map<String, Long> byAgent, Instant oldest, Instant newest,
                             long bytes) {
            this.totalEvents = total;
            this.eventTypeDistribution = Collections.unmodifiableMap(distribution);
            this.eventsByAgent = Collections.unmodifiableMap(byAgent);
            this.oldestEventTime = oldest;
            this.newestEventTime = newest;
            this.storageBytes = bytes;
        }
    }
}
