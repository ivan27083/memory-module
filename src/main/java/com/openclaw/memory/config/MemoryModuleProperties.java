package com.openclaw.memory.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "memory")
public record MemoryModuleProperties(
        @NotNull Duration workingTtl,
        @Min(1) int retrievalLimit,
        @NotNull Http http,
        @NotNull Vector vector,
        @NotNull Embedding embedding
) {
    public record Http(
            @NotNull Duration connectTimeout,
            @NotNull Duration readTimeout
    ) {
    }

    public record Vector(
            @NotBlank String collection,
            @Min(1) int dimensions,
            @NotBlank String qdrantUrl
    ) {
    }

    public record Embedding(
            @NotBlank String baseUrl,
            @NotBlank String model,
            @NotBlank String apiKey
    ) {
    }
}
