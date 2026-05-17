package com.openclaw.memory.adapter.in.web;

import com.openclaw.memory.application.MemoryFacade;
import com.openclaw.memory.domain.model.MemoryType;
import com.openclaw.memory.domain.model.MemoryWriteCommand;
import com.openclaw.memory.domain.model.RetrievalQuery;
import com.openclaw.memory.domain.model.RetrievalResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP (Model Context Protocol) bridge — exposes memory tools as HTTP POST endpoints.
 *
 * Implemented tools:
 *   memory.search  — hybrid retrieval via MemoryFacade
 *   memory.store   — write a new memory record
 *
 * Planned (returns 501 until wired up):
 *   memory.update, memory.delete, memory.timeline, memory.conflicts,
 *   memory.explain, memory.forget, memory.pin, memory.stat
 */
@RestController
@RequestMapping("/mcp")
public class McpController {

    private final MemoryFacade memoryFacade;

    public McpController(MemoryFacade memoryFacade) {
        this.memoryFacade = memoryFacade;
    }

    // ── memory.search ─────────────────────────────────────────────────────────

    @PostMapping("/memory.search")
    public List<SearchHit> search(@Valid @RequestBody SearchRequest request) {
        List<RetrievalResult> results = memoryFacade.retrieve(new RetrievalQuery(
                request.agentId(),
                request.sessionId(),
                request.query(),
                request.topK() > 0 ? request.topK() : 10,
                request.metadata() != null ? request.metadata() : Map.of()
        ));

        return results.stream()
                .map(r -> new SearchHit(
                        r.sourceId() != null ? r.sourceId().toString() : null,
                        r.content(),
                        r.score(),
                        r.sourceType() != null ? r.sourceType().name() : null,
                        r.createdAt()
                ))
                .toList();
    }

    // ── memory.store ──────────────────────────────────────────────────────────

    @PostMapping("/memory.store")
    public ResponseEntity<StoreResult> store(@Valid @RequestBody StoreRequest request) {
        var saved = memoryFacade.write(new MemoryWriteCommand(
                request.agentId(),
                request.sessionId(),
                request.type() != null ? request.type() : MemoryType.EPISODIC,
                request.content(),
                request.metadata() != null ? request.metadata() : Map.of()
        ));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new StoreResult(saved.id().toString(), saved.type().name(), saved.createdAt()));
    }

    // ── not-yet-implemented tools ─────────────────────────────────────────────

    @PostMapping({
            "/memory.update", "/memory.delete", "/memory.timeline",
            "/memory.conflicts", "/memory.explain", "/memory.forget",
            "/memory.pin", "/memory.stat"
    })
    public ResponseEntity<NotImplemented> notImplemented() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(new NotImplemented("This MCP tool is not yet implemented"));
    }

    // ── Request / Response records ────────────────────────────────────────────

    public record SearchRequest(
            @NotBlank String agentId,
            String sessionId,
            @NotBlank String query,
            int topK,
            Map<String, Object> metadata
    ) {}

    public record SearchHit(
            String memoryId,
            String content,
            double score,
            String type,
            Instant createdAt
    ) {}

    public record StoreRequest(
            @NotBlank String agentId,
            String sessionId,
            MemoryType type,
            @NotBlank String content,
            Map<String, Object> metadata
    ) {}

    public record StoreResult(
            String memoryId,
            String type,
            Instant createdAt
    ) {}

    public record NotImplemented(String message) {}
}
