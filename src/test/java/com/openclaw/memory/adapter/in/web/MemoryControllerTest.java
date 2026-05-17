package com.openclaw.memory.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.memory.application.MemoryFacade;
import com.openclaw.memory.application.RagIngestionService;
import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.model.MemoryType;
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

@WebMvcTest(MemoryController.class)
class MemoryControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockBean MemoryFacade memoryFacade;
    @MockBean RagIngestionService ragIngestionService;

    // ── write ─────────────────────────────────────────────────────────────────

    @Test
    void write_validRequest_returns201() throws Exception {
        MemoryRecord saved = MemoryRecord.create(
                UUID.randomUUID(), "agent-1", null, MemoryType.EPISODIC, "hello", Map.of(), Instant.now());
        when(memoryFacade.write(any())).thenReturn(saved);

        String body = mapper.writeValueAsString(Map.of(
                "agentId", "agent-1",
                "content", "hello"
        ));

        mvc.perform(post("/api/memory/write")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agentId").value("agent-1"));
    }

    @Test
    void write_missingAgentId_returns400WithViolation() throws Exception {
        String body = mapper.writeValueAsString(Map.of("content", "hello"));

        mvc.perform(post("/api/memory/write")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    void write_missingContent_returns400() throws Exception {
        String body = mapper.writeValueAsString(Map.of("agentId", "agent-1"));

        mvc.perform(post("/api/memory/write")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void write_malformedJson_returns400() throws Exception {
        mvc.perform(post("/api/memory/write")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_request"));
    }

    @Test
    void write_illegalArgumentFromFacade_returns400() throws Exception {
        when(memoryFacade.write(any())).thenThrow(new IllegalArgumentException("agentId is required"));

        String body = mapper.writeValueAsString(Map.of(
                "agentId", "a", "content", "c"));

        mvc.perform(post("/api/memory/write")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"))
                .andExpect(jsonPath("$.message").value("agentId is required"));
    }

    // ── retrieve ──────────────────────────────────────────────────────────────

    @Test
    void retrieve_validRequest_returns200() throws Exception {
        when(memoryFacade.retrieve(any())).thenReturn(List.of());

        String body = mapper.writeValueAsString(Map.of(
                "agentId", "agent-1",
                "prompt", "what happened?",
                "limit", 5
        ));

        mvc.perform(post("/api/memory/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void retrieve_negativelimit_returns400() throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "agentId", "agent-1",
                "prompt", "what?",
                "limit", -1
        ));

        mvc.perform(post("/api/memory/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    // ── fallback ──────────────────────────────────────────────────────────────

    @Test
    void write_unexpectedRuntimeException_returns500() throws Exception {
        when(memoryFacade.write(any())).thenThrow(new RuntimeException("boom"));

        String body = mapper.writeValueAsString(Map.of(
                "agentId", "agent-1", "content", "hello"));

        mvc.perform(post("/api/memory/write")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"));
    }
}
