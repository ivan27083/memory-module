package com.openclaw.memory.regression;

import com.openclaw.memory.MemoryModuleApplication;
import com.openclaw.memory.application.MemoryFacade;
import com.openclaw.memory.domain.model.MemoryRecord;
import com.openclaw.memory.domain.model.MemoryType;
import com.openclaw.memory.domain.model.MemoryWriteCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression test for memory saving functionality.
 * Tests positive, negative, and edge case scenarios for saving memory entries.
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
        agentId = "test-agent-" + System.nanoTime();
        sessionId = "test-session-" + System.nanoTime();
    }

    @Test
    @DisplayName("Save memory - positive scenario with episodic memory")
    void testSaveMemory_Positive_Episodic() {
        // Given
        String content = "Test memory content about regression testing";
        
        // When
        MemoryRecord saved = memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                sessionId,
                MemoryType.EPISODIC,
                content,
                Map.of("test", "positive", "category", "regression")
            )
        );

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.content()).isEqualTo(content);
        assertThat(saved.agentId()).isEqualTo(agentId);
        assertThat(saved.sessionId()).isEqualTo(sessionId);
        assertThat(saved.metadata()).containsKey("test");
        assertThat(saved.metadata().get("test")).isEqualTo("positive");
        assertThat(saved.createdAt()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(value = MemoryType.class, names = {"WORKING", "EPISODIC", "SEMANTIC_WIKI"})
    @DisplayName("Save memory - all writable memory types")
    void testSaveMemory_AllTypes(MemoryType memoryType) {
        // Given
        String content = "Test memory for type: " + memoryType.name();
        
        // When
        MemoryRecord saved = memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                sessionId,
                memoryType,
                content,
                Map.of("type", memoryType.name())
            )
        );

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.content()).isEqualTo(content);
    }

    @Test
    @DisplayName("Save memory - positive scenario with large content")
    void testSaveMemory_Positive_LargeContent() {
        // Given
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            largeContent.append("This is a test memory entry with substantial content. ");
        }
        
        // When
        MemoryRecord saved = memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                sessionId,
                MemoryType.EPISODIC,
                largeContent.toString(),
                Map.of("size", "large")
            )
        );

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.content().length()).isGreaterThan(1000);
    }

    @Test
    @DisplayName("Save memory - positive scenario with complex metadata")
    void testSaveMemory_Positive_ComplexMetadata() {
        // Given
        String content = "Test memory with complex metadata";
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("priority", 5);
        metadata.put("tags", "important,urgent");
        metadata.put("nested", Map.of("field1", "value1", "field2", 42));
        metadata.put("timestamp", System.currentTimeMillis());
        
        // When
        MemoryRecord saved = memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                sessionId,
                MemoryType.EPISODIC,
                content,
                metadata
            )
        );

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.metadata()).containsAllEntriesOf(metadata);
    }

    @Test
    @DisplayName("Save memory - negative scenario with null content")
    void testSaveMemory_Negative_NullContent() {
        // Given
        String content = null;
        
        // When & Then
        assertThatThrownBy(() -> memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                sessionId,
                MemoryType.EPISODIC,
                content,
                Map.of()
            )
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Save memory - negative scenario with empty content")
    void testSaveMemory_Negative_EmptyContent() {
        // Given
        String content = "";
        
        // When & Then
        assertThatThrownBy(() -> memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                sessionId,
                MemoryType.EPISODIC,
                content,
                Map.of()
            )
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Save memory - negative scenario with whitespace-only content")
    void testSaveMemory_Negative_WhitespaceOnlyContent() {
        // Given
        String content = "   \t\n  ";
        
        // When & Then
        assertThatThrownBy(() -> memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                sessionId,
                MemoryType.EPISODIC,
                content,
                Map.of()
            )
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Save memory - negative scenario with null agent ID")
    void testSaveMemory_Negative_NullAgentId() {
        // Given
        String content = "Valid content";
        
        // When & Then
        assertThatThrownBy(() -> memoryFacade.write(
            new MemoryWriteCommand(
                null,
                sessionId,
                MemoryType.EPISODIC,
                content,
                Map.of()
            )
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Save memory - null session ID is accepted (cross-session/global scope)")
    void testSaveMemory_Negative_NullSessionId() {
        // DefaultMemoryFacade does not require sessionId — null means cross-session scope.
        // JdbcEpisodicMemoryRepository.findRecent() uses agentId-only SQL when sessionId is null.
        MemoryRecord saved = memoryFacade.write(new MemoryWriteCommand(
            agentId, null, MemoryType.EPISODIC, "Valid content", Map.of()
        ));
        assertThat(saved).isNotNull();
    }

    @Test
    @DisplayName("Save memory - null memory type defaults to EPISODIC")
    void testSaveMemory_Negative_NullMemoryType() {
        // DefaultMemoryFacade.write() converts null type to EPISODIC rather than rejecting it.
        MemoryRecord saved = memoryFacade.write(new MemoryWriteCommand(
            agentId, sessionId, null, "Valid content", Map.of()
        ));
        assertThat(saved).isNotNull();
        assertThat(saved.type()).isEqualTo(MemoryType.EPISODIC);
    }

    @Test
    @DisplayName("Save memory - positive scenario with empty metadata")
    void testSaveMemory_Positive_EmptyMetadata() {
        // Given
        String content = "Test memory with empty metadata";
        
        // When
        MemoryRecord saved = memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                sessionId,
                MemoryType.EPISODIC,
                content,
                Map.of()
            )
        );

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.content()).isEqualTo(content);
        assertThat(saved.metadata()).isNotNull();
    }

    @Test
    @DisplayName("Save memory - positive scenario with special characters")
    void testSaveMemory_Positive_SpecialCharacters() {
        // Given
        String content = "Test memory with special chars: @#$%^&*()_+-=[]{}|;':\",./<>?";
        
        // When
        MemoryRecord saved = memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                sessionId,
                MemoryType.EPISODIC,
                content,
                Map.of()
            )
        );

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.content()).isEqualTo(content);
    }

    @Test
    @DisplayName("Save memory - positive scenario with unicode content")
    void testSaveMemory_Positive_UnicodeContent() {
        // Given
        String content = "Test memory with unicode: 你好世界 🌍 مرحبا العالم Привет мир";
        
        // When
        MemoryRecord saved = memoryFacade.write(
            new MemoryWriteCommand(
                agentId,
                sessionId,
                MemoryType.EPISODIC,
                content,
                Map.of()
            )
        );

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.content()).isEqualTo(content);
    }
}