package com.openclaw.memory.application;

import com.openclaw.memory.agents.conflict.ConflictDetector;
import com.openclaw.memory.config.MemoryModuleProperties;
import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.model.MemoryType;
import com.openclaw.memory.domain.model.MemoryWriteCommand;
import com.openclaw.memory.domain.model.RetrievalQuery;
import com.openclaw.memory.domain.model.RetrievalResult;
import com.openclaw.memory.domain.port.EpisodicMemoryRepository;
import com.openclaw.memory.domain.port.MemoryConsolidationPort;
import com.openclaw.memory.domain.port.MemoryGraphPort;
import com.openclaw.memory.domain.port.SemanticWikiRepository;
import com.openclaw.memory.domain.port.WorkingMemoryStore;
import com.openclaw.memory.event_store.Event;
import com.openclaw.memory.event_store.EventStore;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DefaultMemoryFacade implements MemoryFacade {

    private static final Logger log = LoggerFactory.getLogger(DefaultMemoryFacade.class);

    private final MemoryModuleProperties properties;
    private final WorkingMemoryStore workingMemoryStore;
    private final EpisodicMemoryRepository episodicMemoryRepository;
    private final SemanticWikiRepository semanticWikiRepository;
    private final RetrievalOrchestrator retrievalOrchestrator;
    private final MemoryConsolidationPort consolidationService;
    private final EventStore eventStore;
    private final ConflictDetector conflictDetector;
    private final MemoryGraphPort graphPort;
    private final MemoryMetrics metrics;

    public DefaultMemoryFacade(
            MemoryModuleProperties properties,
            WorkingMemoryStore workingMemoryStore,
            EpisodicMemoryRepository episodicMemoryRepository,
            SemanticWikiRepository semanticWikiRepository,
            RetrievalOrchestrator retrievalOrchestrator,
            MemoryConsolidationPort consolidationService,
            EventStore eventStore,
            ConflictDetector conflictDetector,
            MemoryGraphPort graphPort,
            MemoryMetrics metrics
    ) {
        this.properties = properties;
        this.workingMemoryStore = workingMemoryStore;
        this.episodicMemoryRepository = episodicMemoryRepository;
        this.semanticWikiRepository = semanticWikiRepository;
        this.retrievalOrchestrator = retrievalOrchestrator;
        this.consolidationService = consolidationService;
        this.eventStore = eventStore;
        this.conflictDetector = conflictDetector;
        this.graphPort = graphPort;
        this.metrics = metrics;
    }

    @Override
    public MemoryRecord write(MemoryWriteCommand command) {
        validate(command);
        Timer.Sample sample = metrics.startWrite();

        MemoryType type = command.type() == null ? MemoryType.EPISODIC : command.type();
        MemoryRecord record = MemoryRecord.create(
                null,
                command.agentId(),
                command.sessionId(),
                type,
                command.content(),
                command.metadata(),
                null
        );

        List<MemoryRecord> existingWithSubject = preloadSubjectGroup(command, type);

        try {
            MemoryRecord saved = switch (type) {
                case WORKING       -> workingMemoryStore.save(record, properties.workingTtl());
                case EPISODIC      -> episodicMemoryRepository.save(record);
                case SEMANTIC_WIKI -> semanticWikiRepository.upsert(titleFor(record), record);
                case VECTOR, EXTERNAL_RAG -> throw new IllegalArgumentException(
                        "Use RAG ingestion or memory records instead of direct " + type + " writes");
            };

            for (MemoryRecord superseded : existingWithSubject) {
                consolidationService.deleteMemory(superseded.id());
            }

            if (saved.type() == MemoryType.EPISODIC || saved.type() == MemoryType.SEMANTIC_WIKI) {
                consolidationService.indexMemory(saved);
            }

            indexInGraph(saved, existingWithSubject);
            detectAndPublishConflicts(saved, existingWithSubject);
            publishRecorded(saved);
            return saved;
        } finally {
            metrics.stopWrite(sample, type);
        }
    }

    @Override
    public List<RetrievalResult> retrieve(RetrievalQuery query) {
        Timer.Sample sample = metrics.startRetrieval();

        int limit = query.limit() > 0 ? query.limit() : properties.retrievalLimit();
        RetrievalQuery bounded = new RetrievalQuery(
                query.agentId(), query.sessionId(), query.prompt(), limit, query.metadata());

        int resultCount = 0;
        try {
            List<RetrievalResult> results = retrievalOrchestrator.retrieve(bounded);
            publishRetrieved(query, results);
            resultCount = results.size();
            return results;
        } finally {
            metrics.stopRetrieval(sample, resultCount);
        }
    }

    // ── Graph indexing ────────────────────────────────────────────────────────

    private void indexInGraph(MemoryRecord saved, List<MemoryRecord> superseded) {
        try {
            graphPort.addMemory(saved);
            for (MemoryRecord old : superseded) {
                graphPort.recordSupersession(old.id(), saved.id(), java.time.Instant.now());
            }
        } catch (Exception e) {
            log.warn("Graph indexing failed for {}: {}", saved.id(), e.getMessage());
        }
    }

    // ── Conflict detection ────────────────────────────────────────────────────

    private List<MemoryRecord> preloadSubjectGroup(MemoryWriteCommand command, MemoryType type) {
        if (type != MemoryType.EPISODIC || !command.metadata().containsKey("subject")) {
            return List.of();
        }
        try {
            String subject = command.metadata().get("subject").toString();
            return episodicMemoryRepository.findRecent(
                            command.agentId(), command.sessionId(), 50)
                    .stream()
                    .filter(r -> subject.equals(r.metadata().get("subject")))
                    .toList();
        } catch (Exception e) {
            log.warn("Pre-save subject lookup failed: {}", e.getMessage());
            return List.of();
        }
    }

    private void detectAndPublishConflicts(MemoryRecord saved, List<MemoryRecord> existing) {
        if (existing.isEmpty()) return;
        try {
            List<MemoryRecord> allInGroup = Stream
                    .concat(Stream.of(saved), existing.stream())
                    .toList();

            List<ConflictDetector.ConflictReport> conflicts = conflictDetector.detect(allInGroup);

            for (ConflictDetector.ConflictReport report : conflicts) {
                metrics.recordConflict();
                publishConflictDetected(report);
                publishConflictResolved(report);
            }
        } catch (Exception e) {
            log.warn("Conflict detection failed for record {}: {}", saved.id(), e.getMessage());
        }
    }

    // ── Event publishing (best-effort, never fails the caller) ───────────────

    private void publishRecorded(MemoryRecord saved) {
        try {
            eventStore.appendEvent(new Event.Builder()
                    .eventId(UUID.randomUUID().toString())
                    .sourceAgent("DefaultMemoryFacade")
                    .eventType(Event.EventType.MEMORY_RECORDED)
                    .correlationId(correlationId(saved.agentId(), saved.sessionId()))
                    .addPayloadEntry("memoryId",       saved.id().toString())
                    .addPayloadEntry("agentId",        saved.agentId())
                    .addPayloadEntry("sessionId",      saved.sessionId())
                    .addPayloadEntry("type",           saved.type().name())
                    .addPayloadEntry("contentPreview", preview(saved.content()))
                    .build());
        } catch (Exception e) {
            log.warn("Failed to publish MEMORY_RECORDED for {}: {}", saved.id(), e.getMessage());
        }
    }

    private void publishRetrieved(RetrievalQuery query, List<RetrievalResult> results) {
        if (results.isEmpty()) return;
        try {
            List<String> ids = results.stream()
                    .map(r -> r.sourceId() != null ? r.sourceId().toString() : "unknown")
                    .toList();

            eventStore.appendEvent(new Event.Builder()
                    .eventId(UUID.randomUUID().toString())
                    .sourceAgent("DefaultMemoryFacade")
                    .eventType(Event.EventType.MEMORY_RETRIEVED)
                    .correlationId(correlationId(query.agentId(), query.sessionId()))
                    .addPayloadEntry("agentId",     query.agentId())
                    .addPayloadEntry("sessionId",   query.sessionId())
                    .addPayloadEntry("prompt",      preview(query.prompt()))
                    .addPayloadEntry("resultCount", results.size())
                    .addPayloadEntry("resultIds",   ids)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to publish MEMORY_RETRIEVED: {}", e.getMessage());
        }
    }

    private void publishConflictDetected(ConflictDetector.ConflictReport report) {
        try {
            eventStore.appendEvent(new Event.Builder()
                    .eventId(UUID.randomUUID().toString())
                    .sourceAgent("DefaultMemoryFacade")
                    .eventType(Event.EventType.CONFLICT_DETECTED)
                    .correlationId(report.agentId())
                    .addPayloadEntry("agentId",   report.agentId())
                    .addPayloadEntry("subject",   report.subject())
                    .addPayloadEntry("winnerId",  report.winnerId().toString())
                    .addPayloadEntry("loserId",   report.loserId().toString())
                    .addPayloadEntry("type",      report.type().name())
                    .addPayloadEntry("severity",  report.severity())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to publish CONFLICT_DETECTED: {}", e.getMessage());
        }
    }

    private void publishConflictResolved(ConflictDetector.ConflictReport report) {
        try {
            eventStore.appendEvent(new Event.Builder()
                    .eventId(UUID.randomUUID().toString())
                    .sourceAgent("DefaultMemoryFacade")
                    .eventType(Event.EventType.CONFLICT_RESOLVED)
                    .correlationId(report.agentId())
                    .addPayloadEntry("agentId",  report.agentId())
                    .addPayloadEntry("subject",  report.subject())
                    .addPayloadEntry("winnerId", report.winnerId().toString())
                    .addPayloadEntry("reason",   report.resolutionReason())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to publish CONFLICT_RESOLVED: {}", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void validate(MemoryWriteCommand command) {
        if (!StringUtils.hasText(command.agentId())) {
            throw new IllegalArgumentException("agentId is required");
        }
        if (!StringUtils.hasText(command.content())) {
            throw new IllegalArgumentException("content is required");
        }
    }

    private static String titleFor(MemoryRecord record) {
        Object title = record.metadata().get("title");
        if (title instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        String normalized = record.content().strip();
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80);
    }

    private static String correlationId(String agentId, String sessionId) {
        return sessionId != null ? agentId + ":" + sessionId : agentId;
    }

    private static String preview(String text) {
        if (text == null) return "";
        return text.length() <= 120 ? text : text.substring(0, 120) + "…";
    }
}
