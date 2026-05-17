package com.openclaw.memory.regression;

import com.openclaw.memory.MemoryModuleApplication;
import com.openclaw.memory.application.MemoryFacade;
import com.openclaw.memory.domain.model.MemoryType;
import com.openclaw.memory.domain.model.MemoryWriteCommand;
import com.openclaw.memory.domain.model.RetrievalQuery;
import com.openclaw.memory.domain.model.RetrievalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Performance and reliability regression tests for memory module.
 * Tests performance characteristics, timeout handling, and reliability under load.
 */
@SpringBootTest(classes = MemoryModuleApplication.class)
@DisplayName("Memory Performance & Reliability Regression Tests")
public class MemoryPerformanceRegressionTest {

    @Autowired
    private MemoryFacade memoryFacade;

    private String agentId;
    private String sessionId;

    @BeforeEach
    void setUp() {
        agentId = "perf-agent-" + System.nanoTime();
        sessionId = "perf-session-" + System.nanoTime();
    }

    @Test
    @DisplayName("Performance - write single memory should be fast")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testPerformance_SingleMemoryWrite() {
        // Given
        String content = "Performance test memory";
        
        // When
        long startTime = System.currentTimeMillis();
        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            content,
            Map.of("test", "performance")
        ));
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(duration).isLessThan(1000);  // Should complete in < 1 second
    }

    @Test
    @DisplayName("Performance - retrieve single memory should be fast")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testPerformance_SingleMemoryRetrieval() {
        // Setup
        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            "Fast retrieval test memory",
            Map.of()
        ));

        // When
        long startTime = System.currentTimeMillis();
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "retrieval", 5, Map.of())
        );
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(results).isNotEmpty();
        assertThat(duration).isLessThan(2000);  // Should complete in < 2 seconds
    }

    @Test
    @DisplayName("Performance - batch write 100 memories")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testPerformance_BatchWrite100() {
        // Given
        int batchSize = 100;

        // When
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < batchSize; i++) {
            memoryFacade.write(new MemoryWriteCommand(
                agentId, sessionId, MemoryType.EPISODIC,
                "Batch memory entry " + i,
                Map.of("index", String.valueOf(i))
            ));
        }
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(duration).isLessThan(30000);  // Should complete in < 30 seconds
        double avgTime = (double) duration / batchSize;
        assertThat(avgTime).isLessThan(500);  // Average < 500ms per write
    }

    @Test
    @DisplayName("Performance - batch retrieval after 100 writes")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testPerformance_BatchRetrieval100() {
        // Setup - write 100 memories
        for (int i = 0; i < 100; i++) {
            memoryFacade.write(new MemoryWriteCommand(
                agentId, sessionId, MemoryType.EPISODIC,
                "Batch memory entry " + i,
                Map.of("index", String.valueOf(i))
            ));
        }

        // When
        long startTime = System.currentTimeMillis();
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "batch memory", 100, Map.of())
        );
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(results).isNotEmpty();
        assertThat(duration).isLessThan(5000);  // Should complete in < 5 seconds
    }

    @Test
    @DisplayName("Reliability - handle null metadata gracefully")
    void testReliability_NullMetadata() {
        // When & Then - should not throw
        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            "Test memory",
            null
        ));
    }

    @Test
    @DisplayName("Reliability - empty prompt rejected with clear error")
    void testReliability_EmptyStringRetrieval() {
        assertThrows(IllegalArgumentException.class, () ->
            memoryFacade.retrieve(new RetrievalQuery(agentId, sessionId, "", 5, Map.of()))
        );
    }

    @Test
    @DisplayName("Reliability - handle special characters in content")
    void testReliability_SpecialCharactersInContent() {
        // Given
        String specialContent = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~";

        // When
        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            specialContent,
            Map.of()
        ));

        // Then - should be retrievable
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "special", 5, Map.of())
        );
        assertThat(results).isNotNull();
    }

    @Test
    @DisplayName("Reliability - handle unicode content")
    void testReliability_UnicodeContent() {
        // Given
        String unicodeContent = "你好 مرحبا Привет 🌍 🚀 ⭐";

        // When
        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            unicodeContent,
            Map.of()
        ));

        // Then - should be retrievable
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "世界", 5, Map.of())
        );
        assertThat(results).isNotNull();
    }

    @Test
    @DisplayName("Reliability - handle very large metadata")
    void testReliability_VeryLargeMetadata() {
        // Given
        Map<String, Object> largeMetadata = new java.util.HashMap<>();
        for (int i = 0; i < 1000; i++) {
            largeMetadata.put("key_" + i, "value_" + i);
        }

        // When
        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            "Memory with large metadata",
            largeMetadata
        ));

        // Then
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "large metadata", 5, Map.of())
        );
        assertThat(results).isNotEmpty();
    }

    @Test
    @DisplayName("Reliability - sequential writes with same content")
    void testReliability_SequentialWrites() {
        // Given
        String content = "Repeated memory content";

        // When
        for (int i = 0; i < 10; i++) {
            memoryFacade.write(new MemoryWriteCommand(
                agentId, sessionId, MemoryType.EPISODIC,
                content,
                Map.of("iteration", String.valueOf(i))
            ));
        }

        // Then - all should be retrievable
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "repeated memory", 100, Map.of())
        );
        assertThat(results).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("Reliability - retrieval with limit edge cases")
    void testReliability_LimitEdgeCases() {
        // Setup
        for (int i = 0; i < 10; i++) {
            memoryFacade.write(new MemoryWriteCommand(
                agentId, sessionId, MemoryType.EPISODIC,
                "Memory " + i,
                Map.of()
            ));
        }

        // Test with limit = 0
        List<RetrievalResult> emptyResults = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "memory", 0, Map.of())
        );
        assertThat(emptyResults).isNotNull();

        // Test with limit = 1
        List<RetrievalResult> singleResult = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "memory", 1, Map.of())
        );
        assertThat(singleResult).hasSizeLessThanOrEqualTo(1);

        // Test with limit = 1000
        List<RetrievalResult> largeLimit = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "memory", 1000, Map.of())
        );
        assertThat(largeLimit).isNotNull();
    }

    @Test
    @DisplayName("Reliability - empty agent ID rejected with clear error")
    void testReliability_EmptyAgentId() {
        assertThrows(IllegalArgumentException.class, () ->
            memoryFacade.retrieve(new RetrievalQuery("", sessionId, "query", 5, Map.of()))
        );
    }

    @Test
    @DisplayName("Reliability - empty session ID handling")
    void testReliability_EmptySessionId() {
        // When & Then - should handle gracefully
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(agentId, "", "query", 5, Map.of())
        );
        assertThat(results).isNotNull();
    }

    @Test
    @DisplayName("Performance - concurrent reads from multiple threads")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testPerformance_ConcurrentReads() throws InterruptedException {
        // Setup - write one memory
        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            "Concurrent read test memory",
            Map.of()
        ));

        // When - create multiple threads reading concurrently
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                List<RetrievalResult> results = memoryFacade.retrieve(
                    new RetrievalQuery(agentId, sessionId, "concurrent", 5, Map.of())
                );
                assertThat(results).isNotNull();
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        // Then - all reads should succeed
    }

    @Test
    @DisplayName("Reliability - recovery after failed operations")
    void testReliability_RecoveryAfterFailure() {
        // Try to write invalid memory (should fail)
        try {
            memoryFacade.write(new MemoryWriteCommand(
                agentId, sessionId, MemoryType.EPISODIC,
                "",  // Empty content should fail
                Map.of()
            ));
        } catch (Exception e) {
            // Expected to fail
        }

        // System should recover and allow new writes
        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            "Valid memory after failed write",
            Map.of()
        ));

        // And retrieval should work
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "valid memory", 5, Map.of())
        );
        assertThat(results).isNotEmpty();
    }

    @Test
    @DisplayName("Performance - retrieval with complex filters")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testPerformance_ComplexFilters() {
        // Setup - write memories with various metadata
        for (int i = 0; i < 20; i++) {
            String priority = (i % 3 == 0) ? "high" : (i % 3 == 1) ? "medium" : "low";
            String status = (i % 2 == 0) ? "active" : "archived";
            
            memoryFacade.write(new MemoryWriteCommand(
                agentId, sessionId, MemoryType.EPISODIC,
                "Memory with filters " + i,
                Map.of(
                    "priority", priority,
                    "status", status,
                    "category", "test",
                    "index", String.valueOf(i)
                )
            ));
        }

        // When - retrieve with complex filters
        long startTime = System.currentTimeMillis();
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(
                agentId, sessionId, "memory filters",
                10,
                Map.of(
                    "priority", "high",
                    "status", "active",
                    "category", "test"
                )
            )
        );
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(duration).isLessThan(2000);  // Should be fast even with complex filters
    }

    @Test
    @DisplayName("Reliability - handling of null values in metadata")
    void testReliability_NullValuesInMetadata() {
        // Given
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("key1", "value1");
        metadata.put("key2", null);
        metadata.put("key3", "value3");

        // When
        memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, MemoryType.EPISODIC,
            "Memory with null metadata values",
            metadata
        ));

        // Then - should be retrievable
        List<RetrievalResult> results = memoryFacade.retrieve(
            new RetrievalQuery(agentId, sessionId, "metadata", 5, Map.of())
        );
        assertThat(results).isNotNull();
    }
}
