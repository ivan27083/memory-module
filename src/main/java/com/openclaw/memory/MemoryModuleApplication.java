package com.openclaw.memory;

import com.openclaw.memory.config.MemoryModuleProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
@EnableConfigurationProperties(MemoryModuleProperties.class)
public class MemoryModuleApplication {

    public static void main(String[] args) {
        SpringApplication.run(MemoryModuleApplication.class, args);
    }
}
