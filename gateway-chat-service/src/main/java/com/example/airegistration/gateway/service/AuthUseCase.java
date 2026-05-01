package com.example.airegistration.gateway.service;

import reactor.core.publisher.Mono;

public interface AuthUseCase {

    Mono<AuthTokenService.LoginSession> loginByWechat(String code, String nickname, String avatarUrl);

    Mono<Boolean> logout(String token);
}
