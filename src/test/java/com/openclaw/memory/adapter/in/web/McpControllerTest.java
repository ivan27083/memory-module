package com.openclaw.memory.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.memory.application.MemoryFacade;
import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.model.MemoryType;
import com.openclaw.memory.domain.model.RetrievalResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(McpController.class)
class McpControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean  MemoryFacade memoryFacade;

    // ── memory.search ─────────────────────────────────────────────────────────

    @Test
    void search_validRequest_returnsHits() throws Exception {
        UUID id = UUID.randomUUID();
        when(memoryFacade.retrieve(any())).thenReturn(List.of(
                new RetrievalResult(id, MemoryType.EPISODIC, "Paris is the capital", 0.9, Map.of(), Instant.now())
        ));

        String body = mapper.writeValueAsString(Map.of(
                "agentId", "agent-1",
                "query",   "capital of France",
                "topK",    5
        ));

        mvc.perform(post("/mcp/memory.search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Paris is the capital"))
                .andExpect(jsonPath("$[0].score").value(0.9))
                .andExpect(jsonPath("$[0].memoryId").value(id.toString()));
    }

    @Test
    void search_missingAgentId_returns400() throws Exception {
        String body = mapper.writeValueAsString(Map.of("query", "something"));

        mvc.perform(post("/mcp/memory.search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void search_emptyResults_returnsEmptyArray() throws Exception {
        when(memoryFacade.retrieve(any())).thenReturn(List.of());

        String body = mapper.writeValueAsString(Map.of(
                "agentId", "agent-1",
                "query",   "unknown topic"
        ));

        mvc.perform(post("/mcp/memory.search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── memory.store ──────────────────────────────────────────────────────────

    @Test
    void store_validRequest_returns201() throws Exception {
        UUID id = UUID.randomUUID();
        MemoryRecord saved = MemoryRecord.create(
                id, "agent-1", null, MemoryType.EPISODIC, "Rome founded 753 BC", Map.of(), Instant.now());
        when(memoryFacade.write(any())).thenReturn(saved);

        String body = mapper.writeValueAsString(Map.of(
                "agentId",  "agent-1",
                "content",  "Rome founded 753 BC"
        ));

        mvc.perform(post("/mcp/memory.store")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memoryId").value(id.toString()))
                .andExpect(jsonPath("$.type").value("EPISODIC"));
    }

    @Test
    void store_missingContent_returns400() throws Exception {
        String body = mapper.writeValueAsString(Map.of("agentId", "agent-1"));

        mvc.perform(post("/mcp/memory.store")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── not-implemented tools ─────────────────────────────────────────────────

    @Test
    void memoryUpdate_returns501() throws Exception {
        mvc.perform(post("/mcp/memory.update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void memoryStat_returns501() throws Exception {
        mvc.perform(post("/mcp/memory.stat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotImplemented());
    }
}
