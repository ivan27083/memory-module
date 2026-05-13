package com.openclaw.memory.adapter.in.web;

import com.openclaw.memory.application.MemoryFacade;
import com.openclaw.memory.application.RagIngestionService;
import com.openclaw.memory.domain.model.DocumentChunk;
import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.model.MemoryType;
import com.openclaw.memory.domain.model.MemoryWriteCommand;
import com.openclaw.memory.domain.model.RetrievalQuery;
import com.openclaw.memory.domain.model.RetrievalResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MemoryController {

    private final MemoryFacade memoryFacade;
    private final RagIngestionService ragIngestionService;

    public MemoryController(MemoryFacade memoryFacade, RagIngestionService ragIngestionService) {
        this.memoryFacade = memoryFacade;
        this.ragIngestionService = ragIngestionService;
    }

    @PostMapping("/memory/write")
    MemoryRecord write(@Valid @RequestBody WriteMemoryRequest request) {
        return memoryFacade.write(new MemoryWriteCommand(
                request.agentId(),
                request.sessionId(),
                request.type(),
                request.content(),
                request.metadata()
        ));
    }

    @PostMapping("/memory/retrieve")
    List<RetrievalResult> retrieve(@Valid @RequestBody RetrieveMemoryRequest request) {
        return memoryFacade.retrieve(new RetrievalQuery(
                request.agentId(),
                request.sessionId(),
                request.prompt(),
                request.limit(),
                request.metadata()
        ));
    }

    @PostMapping("/rag/ingest")
    List<DocumentChunk> ingest(@Valid @RequestBody IngestDocumentRequest request) {
        return ragIngestionService.ingest(
                request.source(),
                request.title(),
                request.content(),
                request.metadata()
        );
    }

    public record WriteMemoryRequest(
            @NotBlank String agentId,
            String sessionId,
            MemoryType type,
            @NotBlank String content,
            Map<String, Object> metadata
    ) {
    }

    public record RetrieveMemoryRequest(
            @NotBlank String agentId,
            String sessionId,
            @NotBlank String prompt,
            int limit,
            Map<String, Object> metadata
    ) {
    }

    public record IngestDocumentRequest(
            @NotBlank String source,
            @NotBlank String title,
            @NotBlank String content,
            Map<String, Object> metadata
    ) {
    }
}
