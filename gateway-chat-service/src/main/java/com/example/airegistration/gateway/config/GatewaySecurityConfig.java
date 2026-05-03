package com.example.airegistration.gateway.config;

import com.example.airegistration.gateway.service.AuthTokenService;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                         AuthTokenService authTokenService,
                                                         AuthProperties authProperties) {
        if (!authProperties.isEnabled()) {
            return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                    .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                    .logout(ServerHttpSecurity.LogoutSpec::disable)
                    .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
                    .build();
        }

        AuthenticationWebFilter tokenFilter = new AuthenticationWebFilter(tokenAuthenticationManager(authTokenService));
        tokenFilter.setServerAuthenticationConverter(exchange -> {
            String token = authTokenService.readToken(exchange);
            if (token == null || token.isBlank()) {
                return Mono.empty();
            }
            return Mono.just(UsernamePasswordAuthenticationToken.unauthenticated(token, token));
        });
        tokenFilter.setRequiresAuthenticationMatcher(ServerWebExchangeMatchers.pathMatchers(
                "/api/chat",
                "/api/patients",
                "/api/patients/**",
                "/api/registrations",
                "/api/registrations/**",
                "/api/traces/**",
                "/api/auth/logout",
                "/api/auth/me"
        ));

        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/api/auth/wechat-login", "/actuator/**").permitAll()
                        .pathMatchers(
                                "/api/chat",
                                "/api/patients",
                                "/api/patients/**",
                                "/api/registrations",
                                "/api/registrations/**",
                                "/api/traces/**",
                                "/api/auth/logout",
                                "/api/auth/me"
                        ).authenticated()
                        .anyExchange().permitAll())
                .addFilterAt(tokenFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    private ReactiveAuthenticationManager tokenAuthenticationManager(AuthTokenService authTokenService) {
        return authentication -> authTokenService.resolveUserId(String.valueOf(authentication.getCredentials()))
                .map(userId -> new UsernamePasswordAuthenticationToken(
                        userId,
                        authentication.getCredentials(),
                        List.of(new SimpleGrantedAuthority("ROLE_PATIENT"))
                ))
                .cast(Authentication.class)
                .switchIfEmpty(Mono.error(new BadCredentialsException("登录状态无效，请重新登录。")));
    }
}
