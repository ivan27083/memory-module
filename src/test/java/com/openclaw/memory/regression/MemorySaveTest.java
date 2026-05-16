package com.openclaw.memory.regression;

import com.openclaw.memory.MemoryModuleApplication;
import com.openclaw.memory.application.MemoryFacade;
import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.model.MemoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for memory saving functionality.
 * Tests both positive and negative scenarios for saving memory entries.
 */
@SpringBootTest(classes = MemoryModuleApplication.class)
@DisplayName("Memory Save Regression Tests")
public class MemorySaveTest {

    @Autowired
    private MemoryFacade memoryFacade;

    private String agentId;
    private String sessionId;

    @BeforeEach
    void setUp() {
        agentId = "test-agent";
        sessionId = "test-session";
    }

    @Test
    @DisplayName("Save memory - positive scenario")
    void testSaveMemory_Positive() {
        // Given
        String content = "Test memory content about regression testing";
        
        // When
        MemoryRecord saved = memoryFacade.write(
            agentId,
            sessionId,
            MemoryType.EPISODIC,
            content,
            Map.of("test", "positive")
        );

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.content()).isEqualTo(content);
        assertThat(saved.agentId()).isEqualTo(agentId);
        assertThat(saved.sessionId()).isEqualTo(sessionId);
        assertThat(saved.metadata()).containsKey("test");
    }

    @Test
    @DisplayName("Save memory - negative scenario with null content")
    void testSaveMemory_Negative_NullContent() {
        // Given
        String content = null;
        
        // When & Then
        assertThatThrownBy(() -> memoryFacade.write(
            agentId,
            sessionId,
            MemoryType.EPISODIC,
            content,
            Map.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Save memory - negative scenario with empty content")
    void testSaveMemory_Negative_EmptyContent() {
        // Given
        String content = "";
        
        // When & Then
        assertThatThrownBy(() -> memoryFacade.write(
            agentId,
            sessionId,
            MemoryType.EPISODIC,
            content,
            Map.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }
}