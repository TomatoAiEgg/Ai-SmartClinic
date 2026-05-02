package com.example.airegistration.knowledge.config;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class KnowledgeAdminTokenFilter implements WebFilter {

    private final KnowledgeAdminProperties properties;

    public KnowledgeAdminTokenFilter(KnowledgeAdminProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!isProtected(exchange) || properties.getToken().isBlank()) {
            return chain.filter(exchange);
        }

        String providedToken = exchange.getRequest().getHeaders().getFirst(properties.getHeaderName());
        if (properties.getToken().equals(providedToken)) {
            return chain.filter(exchange);
        }

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private boolean isProtected(ServerWebExchange exchange) {
        return exchange.getRequest().getPath().pathWithinApplication().value().startsWith("/api/knowledge");
    }
}
