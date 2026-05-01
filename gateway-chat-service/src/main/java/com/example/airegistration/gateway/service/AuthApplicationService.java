package com.example.airegistration.gateway.service;

import com.example.airegistration.gateway.repository.UserAccountRepository;
import com.example.airegistration.gateway.client.WechatSessionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class AuthApplicationService implements AuthUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuthApplicationService.class);

    private final AuthTokenService authTokenService;
    private final WechatSessionClient wechatSessionClient;
    private final UserAccountRepository userAccountRepository;

    public AuthApplicationService(
            AuthTokenService authTokenService,
            WechatSessionClient wechatSessionClient,
            UserAccountRepository userAccountRepository) {
        this.authTokenService = authTokenService;
        this.wechatSessionClient = wechatSessionClient;
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public Mono<AuthTokenService.LoginSession> loginByWechat(String code, String nickname, String avatarUrl) {
        log.info(
                "[auth] wechat authentication started, codeLength={}, nicknameProvided={}, avatarProvided={}",
                textLength(code),
                hasText(nickname),
                hasText(avatarUrl)
        );
        return wechatSessionClient.exchangeCode(code)
                .doOnSuccess(session -> log.info(
                        "[auth] wechat code exchanged, openIdFingerprint={}, unionIdProvided={}",
                        fingerprint(session.openId()),
                        hasText(session.unionId())
                ))
                .flatMap(session -> Mono.fromCallable(() -> userAccountRepository.upsertWechatUser(
                                session.openId(),
                                session.unionId(),
                                nickname,
                                avatarUrl
                        ))
                        .subscribeOn(Schedulers.boundedElastic()))
                .flatMap(authTokenService::createSession)
                .doOnSuccess(session -> log.info("[auth] wechat session created, userId={}", session.userId()))
                .doOnError(error -> log.warn("[auth] wechat authentication failed, reason={}", error.getMessage()));
    }

    @Override
    public Mono<Boolean> logout(String token) {
        log.info("[auth] logout started, tokenProvided={}", hasText(token));
        return authTokenService.revoke(token)
                .doOnSuccess(success -> log.info("[auth] logout completed, success={}", success));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int textLength(String value) {
        return value == null ? 0 : value.length();
    }

    private String fingerprint(String value) {
        if (!hasText(value)) {
            return "<blank>";
        }
        int hash = value.hashCode();
        return Integer.toHexString(hash);
    }
}
