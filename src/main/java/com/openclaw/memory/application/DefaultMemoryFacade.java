package com.openclaw.memory.application;

import com.openclaw.memory.config.MemoryModuleProperties;
import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.model.MemoryType;
import com.openclaw.memory.domain.model.MemoryWriteCommand;
import com.openclaw.memory.domain.model.RetrievalQuery;
import com.openclaw.memory.domain.model.RetrievalResult;
import com.openclaw.memory.domain.port.EpisodicMemoryRepository;
import com.openclaw.memory.domain.port.SemanticWikiRepository;
import com.openclaw.memory.domain.port.WorkingMemoryStore;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DefaultMemoryFacade implements MemoryFacade {

    private final MemoryModuleProperties properties;
    private final WorkingMemoryStore workingMemoryStore;
    private final EpisodicMemoryRepository episodicMemoryRepository;
    private final SemanticWikiRepository semanticWikiRepository;
    private final RetrievalOrchestrator retrievalOrchestrator;
    private final MemoryConsolidationService consolidationService;

    public DefaultMemoryFacade(
            MemoryModuleProperties properties,
            WorkingMemoryStore workingMemoryStore,
            EpisodicMemoryRepository episodicMemoryRepository,
            SemanticWikiRepository semanticWikiRepository,
            RetrievalOrchestrator retrievalOrchestrator,
            MemoryConsolidationService consolidationService
    ) {
        this.properties = properties;
        this.workingMemoryStore = workingMemoryStore;
        this.episodicMemoryRepository = episodicMemoryRepository;
        this.semanticWikiRepository = semanticWikiRepository;
        this.retrievalOrchestrator = retrievalOrchestrator;
        this.consolidationService = consolidationService;
    }

    @Override
    public MemoryRecord write(MemoryWriteCommand command) {
        validate(command);
        MemoryType type = command.type() == null ? MemoryType.EPISODIC : command.type();
        MemoryRecord record = new MemoryRecord(
                null,
                command.agentId(),
                command.sessionId(),
                type,
                command.content(),
                command.metadata(),
                null
        );

        MemoryRecord saved = switch (type) {
            case WORKING -> workingMemoryStore.save(record, properties.workingTtl());
            case EPISODIC -> episodicMemoryRepository.save(record);
            case SEMANTIC_WIKI -> semanticWikiRepository.upsert(titleFor(record), record);
            case VECTOR, EXTERNAL_RAG -> throw new IllegalArgumentException("Use RAG ingestion or memory records instead of direct " + type + " writes");
        };

        if (saved.type() == MemoryType.EPISODIC || saved.type() == MemoryType.SEMANTIC_WIKI) {
            consolidationService.indexMemory(saved);
        }
        return saved;
    }

    @Override
    public List<RetrievalResult> retrieve(RetrievalQuery query) {
        int limit = query.limit() > 0 ? query.limit() : properties.retrievalLimit();
        return retrievalOrchestrator.retrieve(new RetrievalQuery(
                query.agentId(),
                query.sessionId(),
                query.prompt(),
                limit,
                query.metadata()
        ));
    }

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
}
