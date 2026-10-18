package com.fitnessai.file.config;

import com.fitnessai.file.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnProperty(name = "events.redis.enabled", havingValue = "true", matchIfMissing = true)
public class UserDeletedListener {

    private static final Logger log = LoggerFactory.getLogger(UserDeletedListener.class);

    @Bean
    RedisMessageListenerContainer userDeletedListenerContainer(RedisConnectionFactory factory, FileService files) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener((message, pattern) -> {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            try {
                files.deleteAllForUser(Long.parseLong(body));
                log.info("Deleted files for removed user {}", body);
            } catch (NumberFormatException e) {
                log.warn("Ignoring malformed user-deleted event: {}", body);
            }
        }, new ChannelTopic("events:user-deleted"));
        return container;
    }
}
