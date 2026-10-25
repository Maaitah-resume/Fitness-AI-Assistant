package com.fitnessai.chat.config;

import com.fitnessai.chat.memory.ShortTermMemory;
import com.fitnessai.chat.service.ConversationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import com.fitnessai.chat.repository.WeeklyReportRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableAsync
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    public static final String MISSING_KEY = "not-configured";

    @Value("${spring.ai.google.genai.api-key}")
    private String apiKey;

    @EventListener(ApplicationReadyEvent.class)
    void warnIfNoApiKey() {
        if (MISSING_KEY.equals(apiKey)) {
            log.warn("GEMINI_API_KEY is not set — chat replies will use the offline fallback until it is.");
        }
    }

    /** Deletes a removed user's conversations when user-service announces the deletion. */
    @Bean
    @ConditionalOnProperty(name = "events.redis.enabled", havingValue = "true", matchIfMissing = true)
    RedisMessageListenerContainer userDeletedListenerContainer(RedisConnectionFactory factory,
                                                               ConversationStore store, ShortTermMemory memory,
                                                               WeeklyReportRepository reports,
                                                               com.fitnessai.chat.service.UserContextService userContext) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener((message, pattern) -> {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            try {
                memory.clear(store.deleteAllForUser(Long.parseLong(body)));
                reports.deleteByUserId(Long.parseLong(body));
                userContext.deleteForUser(Long.parseLong(body));
                log.info("Deleted conversations, weekly reports and memories for removed user {}", body);
            } catch (NumberFormatException e) {
                log.warn("Ignoring malformed user-deleted event: {}", body);
            }
        }, new ChannelTopic("events:user-deleted"));
        return container;
    }
}
