package com.fitnessai.knowledge.config;

import com.fitnessai.knowledge.graph.GraphRepository;
import com.fitnessai.knowledge.security.Caller;
import com.fitnessai.knowledge.vector.VectorIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/** Purges a deleted user's private documents, graph contributions and long-term memories. */
@Configuration
@ConditionalOnProperty(name = "events.redis.enabled", havingValue = "true", matchIfMissing = true)
public class UserDeletedListener {

    private static final Logger log = LoggerFactory.getLogger(UserDeletedListener.class);

    @Bean
    RedisMessageListenerContainer userDeletedListenerContainer(RedisConnectionFactory factory,
                                                               VectorIndexService vectors, GraphRepository graph) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener((message, pattern) -> {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            long userId;
            try {
                userId = Long.parseLong(body);
            } catch (NumberFormatException e) {
                log.warn("Ignoring malformed user-deleted event: {}", body);
                return;
            }
            String scope = new Caller(userId, false).ownScope();
            try {
                vectors.deleteScope(scope);
            } catch (Exception e) {
                log.error("Failed to purge vectors for deleted user {}: {}", userId, e.getMessage());
            }
            try {
                graph.removeScope(scope);
            } catch (Exception e) {
                log.error("Failed to purge graph data for deleted user {}: {}", userId, e.getMessage());
            }
            log.info("Purged knowledge data for removed user {}", userId);
        }, new ChannelTopic("events:user-deleted"));
        return container;
    }
}
