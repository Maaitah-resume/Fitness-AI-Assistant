package com.fitnessai.knowledge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    public static final String MISSING_KEY = "not-configured";

    @Value("${spring.ai.google.genai.api-key}")
    private String apiKey;

    /** Small, bounded pool: graph extraction is LLM-bound, and a burst of uploads shouldn't fan out unbounded. */
    @Bean
    Executor graphExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(100);
        ex.setThreadNamePrefix("graph-");
        ex.initialize();
        return ex;
    }

    @EventListener(ApplicationReadyEvent.class)
    void warnIfNoApiKey() {
        if (MISSING_KEY.equals(apiKey)) {
            log.warn("GEMINI_API_KEY is not set — embeddings and graph extraction will fail until it is.");
        }
    }
}
