package com.fitnessai.profile.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/** Drops a user's profile when user-service publishes that the user was deleted. */
@Configuration
@ConditionalOnProperty(name = "events.redis.enabled", havingValue = "true", matchIfMissing = true)
public class UserDeletedListener {

    private static final Logger log = LoggerFactory.getLogger(UserDeletedListener.class);
    static final String CHANNEL = "events:user-deleted";

    @Bean
    RedisMessageListenerContainer userDeletedListenerContainer(RedisConnectionFactory factory,
                                                               ProfileService profiles) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener((message, pattern) -> {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            try {
                profiles.deleteForUser(Long.parseLong(body));
                log.info("Deleted profile for removed user {}", body);
            } catch (NumberFormatException e) {
                log.warn("Ignoring malformed user-deleted event: {}", body);
            }
        }, new ChannelTopic(CHANNEL));
        return container;
    }
}
