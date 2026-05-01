package com.example.airegistration.gateway.controller;

import com.example.airegistration.gateway.config.AuthProperties;
import com.example.airegistration.gateway.dto.CurrentUserResponse;
import com.example.airegistration.gateway.dto.LoginResponse;
import com.example.airegistration.gateway.dto.WechatLoginRequest;
import com.example.airegistration.gateway.service.AuthTokenService;
import com.example.airegistration.gateway.service.AuthUseCase;
import java.security.Principal;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
public class GatewayAuthController {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuthController.class);

    private final AuthUseCase authUseCase;
    private final AuthTokenService authTokenService;
    private final AuthProperties authProperties;

    public GatewayAuthController(AuthUseCase authUseCase,
                                 AuthTokenService authTokenService,
                                 AuthProperties authProperties) {
        this.authUseCase = authUseCase;
        this.authTokenService = authTokenService;
        this.authProperties = authProperties;
    }

    @PostMapping("/wechat-login")
    public Mono<LoginResponse> wechatLogin(@RequestBody WechatLoginRequest request) {
        log.info(
                "[auth] wechat login request received, codeLength={}, nicknameProvided={}, avatarProvided={}",
                textLength(request.code()),
                hasText(request.nickname()),
                hasText(request.avatarUrl())
        );
        return authUseCase.loginByWechat(request.code(), request.nickname(), request.avatarUrl())
                .map(this::toLoginResponse)
                .doOnSuccess(response -> log.info(
                        "[auth] wechat login response ready, userId={}, expiresIn={}s",
                        response.userId(),
                        response.expiresIn()
                ));
    }

    @PostMapping("/logout")
    public Mono<Map<String, Object>> logout(ServerWebExchange exchange) {
        String token = authTokenService.readToken(exchange);
        log.info("[auth] logout request received, tokenProvided={}", hasText(token));
        return authUseCase.logout(token)
                .doOnSuccess(success -> log.info("[auth] logout finished, success={}", success))
                .map(success -> Map.of("success", success));
    }

    @GetMapping("/me")
    public Mono<CurrentUserResponse> me(Principal principal) {
        log.info("[auth] current user request, userId={}", principal.getName());
        return Mono.just(new CurrentUserResponse(principal.getName()));
    }

    private LoginResponse toLoginResponse(AuthTokenService.LoginSession session) {
        return new LoginResponse(
                session.token(),
                session.userId(),
                session.expiresIn(),
                authProperties.getTokenHeader()
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int textLength(String value) {
        return value == null ? 0 : value.length();
    }

}
