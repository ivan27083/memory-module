package com.openclaw.memory.adapter.in.web;

import com.openclaw.memory.MemoryModuleApplication;
import com.openclaw.memory.application.MemoryFacade;
import com.openclaw.memory.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты для HTTP API модуля памяти.
 * Исправлено: /rag/ingest возвращает List, а не Map.
 */
@SpringBootTest(classes = MemoryModuleApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Memory API Integration Tests")
public class MemoryApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MemoryFacade memoryFacade;

    @LocalServerPort
    private int port;

    private String baseUrl;

    private String agentId = "test-agent";
    private String sessionId = "test-session";

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
    }

    @Test
    @DisplayName("Память: сохранение и поиск")
    void testMemory_WriteAndRetrieve() {
        // Запись через facade
        MemoryWriteCommand writeCommand = new MemoryWriteCommand(
                agentId,
                sessionId,
                MemoryType.EPISODIC,
                "Тестовая память о Java",
                Map.of("lang", "ru")
        );

        MemoryRecord saved = memoryFacade.write(writeCommand);
        assertThat(saved).isNotNull();
        assertThat(saved.content()).contains("Java");

        // Поиск
        RetrievalQuery query = new RetrievalQuery(
                agentId,
                sessionId,
                "Что я знаю о Java?",
                5,
                Map.of()
        );

        var results = memoryFacade.retrieve(query);
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).content()).contains("Java");
    }

    @Test
    @DisplayName("RAG: загрузка документа (summarization)")
    void testRag_IngestDocument() {
        // Запрос
        Map<String, Object> request = Map.of(
                "source", "user-upload",
                "title", "Введение в Spring Boot",
                "content", "Spring Boot — фреймворк для быстрой разработки на Java. Упрощает настройку и развертывание приложений."
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        // ✅ Исправлено: ожидаем List, а не Map
        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl + "/api/rag/ingest",
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<List>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();

        // Проверим первый элемент
        Map<String, Object> firstChunk = (Map<String, Object>) response.getBody().get(0);
        assertThat(firstChunk).isNotNull();
        assertThat(firstChunk.get("content")).toString().contains("Spring Boot");
    }

    @Test
    @DisplayName("Memory: проверка MCP-подобного поведения через API")
    void testMcpStyleCalls() {
        // 1. Сохранение
        Map<String, Object> writeReq = Map.of(
                "agentId", agentId,
                "sessionId", sessionId,
                "type", "EPISODIC",
                "content", "Пользователь любит Kotlin"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> writeEntity = new HttpEntity<>(writeReq, headers);

        ResponseEntity<MemoryRecord> writeRes = restTemplate.postForEntity(
                baseUrl + "/api/memory/write",
                writeEntity,
                MemoryRecord.class
        );

        assertThat(writeRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(writeRes.getBody()).isNotNull();
        assertThat(writeRes.getBody().content()).contains("Kotlin");

        // 2. Поиск
        Map<String, Object> retrieveReq = Map.of(
                "agentId", agentId,
                "sessionId", sessionId,
                "prompt", "Что я знаю о предпочтениях пользователя?",
                "limit", 5
        );

        HttpEntity<Map<String, Object>> retrieveEntity = new HttpEntity<>(retrieveReq, headers);

        ResponseEntity<List<Map<String, Object>>> retrieveRes = restTemplate.exchange(
                baseUrl + "/api/memory/retrieve",
                HttpMethod.POST,
                retrieveEntity,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );

        assertThat(retrieveRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retrieveRes.getBody()).isNotEmpty();
        assertThat(((Map) retrieveRes.getBody().get(0)).get("content"))
                .toString().contains("Kotlin");
    }
}
