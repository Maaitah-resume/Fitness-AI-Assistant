package com.fitnessai.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.Optional;

@Configuration
public class RateLimitConfig {

    /**
     * Rate-limit bucket: the authenticated user (JWT subject) when there is
     * one, so users behind one NAT don't share a bucket; otherwise the client
     * IP, which covers the public login/register endpoints.
     */
    @Bean
    KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .map(sub -> "user:" + sub)
                .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                        .map(InetSocketAddress::getAddress)
                        .map(a -> a.getHostAddress())
                        .orElse("unknown")));
    }
}
