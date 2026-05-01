package com.example.airegistration.gateway.service;

import com.example.airegistration.gateway.config.AuthProperties;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Service
public class AuthTokenService {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenService.class);

    private static final String TOKEN_PREFIX = "ai-smartclinic:auth:token:";
    private static final int TOKEN_BYTES = 32;

    private final ReactiveStringRedisTemplate redisTemplate;
    private final AuthProperties authProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthTokenService(ReactiveStringRedisTemplate redisTemplate, AuthProperties authProperties) {
        this.redisTemplate = redisTemplate;
        this.authProperties = authProperties;
    }

    public Mono<LoginSession> createSession(String userId) {
        String token = generateToken();
        Duration ttl = authProperties.getTokenTtl();
        log.info("[auth-token] creating session, userId={}, ttl={}s", userId, ttl.toSeconds());
        return redisTemplate.opsForValue()
                .set(redisKey(token), userId, ttl)
                .doOnSuccess(saved -> log.info(
                        "[auth-token] session saved to redis, userId={}, tokenFingerprint={}, saved={}",
                        userId,
                        tokenFingerprint(token),
                        saved
                ))
                .thenReturn(new LoginSession(token, userId, ttl.toSeconds()));
    }

    public Mono<String> resolveUserId(String token) {
        if (token == null || token.isBlank()) {
            log.info("[auth-token] resolve skipped, token missing");
            return Mono.empty();
        }
        String normalizedToken = token.trim();
        return redisTemplate.opsForValue()
                .get(redisKey(normalizedToken))
                .doOnSuccess(userId -> log.info(
                        "[auth-token] resolve finished, tokenFingerprint={}, found={}",
                        tokenFingerprint(normalizedToken),
                        userId != null
                ));
    }

    public Mono<Boolean> revoke(String token) {
        if (token == null || token.isBlank()) {
            log.info("[auth-token] revoke skipped, token missing");
            return Mono.just(false);
        }
        String normalizedToken = token.trim();
        return redisTemplate.delete(redisKey(normalizedToken))
                .map(deleted -> deleted > 0)
                .doOnSuccess(success -> log.info(
                        "[auth-token] revoke finished, tokenFingerprint={}, success={}",
                        tokenFingerprint(normalizedToken),
                        success
                ));
    }

    public String readToken(ServerWebExchange exchange) {
        String configuredHeaderToken = exchange.getRequest().getHeaders().getFirst(authProperties.getTokenHeader());
        if (configuredHeaderToken != null && !configuredHeaderToken.isBlank()) {
            return configuredHeaderToken.trim();
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return null;
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String redisKey(String token) {
        return TOKEN_PREFIX + token;
    }

    private String tokenFingerprint(String token) {
        if (token == null || token.isBlank()) {
            return "<blank>";
        }
        return Integer.toHexString(token.hashCode());
    }

    public record LoginSession(String token, String userId, long expiresIn) {
    }
}
