package com.example.airegistration.gateway;

import com.example.airegistration.gateway.config.AuthProperties;
import com.example.airegistration.gateway.service.AuthTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuthTokenServiceTest {

    private final AuthProperties authProperties = new AuthProperties();
    private final AuthTokenService authTokenService = new AuthTokenService(
            mock(ReactiveStringRedisTemplate.class),
            authProperties
    );

    @Test
    void shouldPreferConfiguredTokenHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/chat")
                .header(authProperties.getTokenHeader(), " token-from-header ")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-from-bearer"));

        String token = authTokenService.readToken(exchange);

        assertThat(token).isEqualTo("token-from-header");
    }

    @Test
    void shouldReadBearerTokenWhenConfiguredHeaderMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/chat")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-from-bearer "));

        String token = authTokenService.readToken(exchange);

        assertThat(token).isEqualTo("token-from-bearer");
    }

    @Test
    void shouldReturnNullWhenTokenMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/chat"));

        String token = authTokenService.readToken(exchange);

        assertThat(token).isNull();
    }
}
