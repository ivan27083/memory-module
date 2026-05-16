package com.openclaw.memory.retrieval;

import com.openclaw.memory.blackboard.Artifact;
import com.openclaw.memory.blackboard.MemoryBlackboard;
import com.openclaw.memory.blackboard.Provenance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetrieverContractTest {

    private Retriever retriever;
    private MemoryBlackboard blackboard;

    @BeforeEach
    void setUp() {
        blackboard = new MemoryBlackboard();
        retriever = new BlackboardRetriever(blackboard);

        // Добавляем тестовые данные
        Provenance p = new Provenance(
            "test-1", "TestAgent", List.of("evt-1"),
            LocalDateTime.now(), 0.9f
        );
        blackboard.storeArtifact(new Artifact(
            "test-1", "test query content", "text",
            LocalDateTime.now(), p, new java.util.HashMap<>()
        ));
    }

    @Test
    void search_shouldReturnResults_whenQueryIsValid() throws Exception {
        List<RetrievalResult> results = retriever.search("test", 5).get();
        assertThat(results).isNotNull();
        assertThat(results.size()).isLessThanOrEqualTo(5);
    }

    @Test
    void search_shouldReturnScoresBetweenZeroAndOne() throws Exception {
        List<RetrievalResult> results = retriever.search("test", 10).get();
        results.forEach(r ->
            assertThat(r.score()).isBetween(0.0, 1.0)
        );
    }

    @Test
    void search_shouldReturnResultsOrderedByScoreDescending() throws Exception {
        List<RetrievalResult> results = retriever.search("test", 10).get();
        for (int i = 1; i < results.size(); i++) {
            assertThat(results.get(i - 1).score())
                .isGreaterThanOrEqualTo(results.get(i).score());
        }
    }

    @Test
    void search_shouldFindStoredContent() throws Exception {
        List<RetrievalResult> results = retriever.search("test query", 5).get();
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).memoryId()).isEqualTo("test-1");
    }
}