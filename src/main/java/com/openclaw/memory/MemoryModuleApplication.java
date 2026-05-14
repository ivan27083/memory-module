package com.openclaw.memory;

import com.openclaw.memory.config.MemoryModuleProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Memory Module Application - Multi-Agent Cognitive Memory Runtime (MACMR)
 * 
 * Это главная точка входа для системы памяти для автономных AI агентов.
 * 
 * Архитектура основана на:
 * - 12 специализированных агентах
 * - Blackboard архитектуре для коммуникации
 * - Append-only Event Store для неизменяемости
 * - Гибридной системе поиска (BM25 + Vector + Rerank)
 * - Управлении провенансом для каждого артефакта
 * 
 * Подробнее: см. docs/ARCHITECTURE_RU.md
 */
@EnableAsync
@SpringBootApplication
@EnableConfigurationProperties(MemoryModuleProperties.class)
public class MemoryModuleApplication {
    
    private static final Logger logger = LoggerFactory.getLogger(MemoryModuleApplication.class);

    public static void main(String[] args) {
        logger.info("Starting Memory Module - Multi-Agent Cognitive Memory Runtime");
        logger.info("Documentation: docs/ARCHITECTURE_RU.md");
        logger.info("Contributing: CONTRIBUTING.md");
        
        SpringApplication.run(MemoryModuleApplication.class, args);
        
        logger.info("Memory Module started successfully");
        logger.info("API available at: http://localhost:8080");
        logger.info("Metrics available at: http://localhost:8080/actuator/prometheus");
    }
}
