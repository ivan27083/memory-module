package com.openclaw.memory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class MemoryConfiguration {

    @Bean
    RestClient.Builder restClientBuilder(MemoryModuleProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMillis(properties.http().connectTimeout()));
        requestFactory.setReadTimeout(timeoutMillis(properties.http().readTimeout()));
        return RestClient.builder()
                .requestFactory(requestFactory);
    }

    private static int timeoutMillis(java.time.Duration duration) {
        long millis = duration.toMillis();
        if (millis > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.toIntExact(Math.max(millis, 1));
    }
}
