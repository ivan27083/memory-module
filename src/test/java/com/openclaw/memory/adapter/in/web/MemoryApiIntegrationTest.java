package com.openclaw.memory.adapter.in.web;

import com.openclaw.memory.MemoryModuleApplication;
import com.openclaw.memory.application.MemoryFacade;
import com.openclaw.memory.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MemoryModuleApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DisplayName("Memory API Integration Tests")
public class MemoryApiIntegrationTest {

    @Autowired
    private WebClient webClient;

    @Autowired
    private MemoryFacade memoryFacade;

    private String agentId = "test-agent";
    private String sessionId = "test-session";

    @BeforeEach
    void setUp() {
        // Очистка (если нужно)
        // memoryFacade.clear(); // если есть такой метод
    }

    @Test
    @DisplayName("Память: сохранение и поиск")
    void testMemory_WriteAndRetrieve() {
        // Запись
        MemoryWriteCommand writeCommand = new MemoryWriteCommand(
                agentId,
                sessionId,
                MemoryType.FACT,
                "Тестовая память о Java",
                Map.of("lang", "ru")
        );

        MemoryRecord saved = memoryFacade.write(writeCommand);
        assertThat(saved).isNotNull();
        assertThat(saved.getContent()).contains("Java");

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
        assertThat(results.get(0).getContent()).contains("Java");
    }

    @Test
    @DisplayName("RAG: загрузка документа (summarization)")
    void testRag_IngestDocument() {
        // Имитация загрузки документа
        var request = Map.of(
                "source", "user-upload",
                "title", "Введение в Spring Boot",
                "content", "Spring Boot — фреймворк для быстрой разработки на Java. Упрощает настройку и развертывание приложений."
        );

        ResponseEntity<DocumentChunk[]> response = webClient.post()
                .uri("/rag/ingest")
                .body(Mono.just(request), Map.class)
                .retrieve()
                .onStatus(HttpStatus::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(error -> Mono.error(new RuntimeException("Error: " + error))))
                .toEntity(DocumentChunk[].class)
                .block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody()[0].getContent()).contains("Spring Boot");
    }

    @Test
    @DisplayName("Memory: проверка MCP-подобного поведения через API")
    void testMcpStyleCalls() {
        // MCP — это скорее интерфейс, но мы тестируем его поведение

        // 1. Сохранение
        var writeReq = Map.of(
                "agentId", agentId,
                "sessionId", sessionId,
                "type", "FACT",
                "content", "Пользователь любит Kotlin"
        );

        ResponseEntity<MemoryRecord> writeRes = webClient.post()
                .uri("/api/memory/write")
                .body(Mono.just(writeReq), Map.class)
                .retrieve()
                .toEntity(MemoryRecord.class)
                .block();

        assertThat(writeRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(writeRes.getBody()).isNotNull();

        // 2. Поиск
        var retrieveReq = Map.of(
                "agentId", agentId,
                "sessionId", sessionId,
                "prompt", "Что я знаю о предпочтениях пользователя?",
                "limit", 5
        );

        ResponseEntity<RetrievalResult[]> retrieveRes = webClient.post()
                .uri("/api/memory/retrieve")
                .body(Mono.just(retrieveReq), Map.class)
                .retrieve()
                .toEntity(RetrievalResult[].class)
                .block();

        assertThat(retrieveRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retrieveRes.getBody()).isNotEmpty();
        assertThat(retrieveRes.getBody()[0].getContent()).contains("Kotlin");
    }
}
