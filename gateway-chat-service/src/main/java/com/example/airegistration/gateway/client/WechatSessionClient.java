package com.example.airegistration.gateway.client;

import com.example.airegistration.enums.ApiErrorCode;
import com.example.airegistration.gateway.config.AuthProperties;
import com.example.airegistration.gateway.exception.WechatLoginException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Component
public class WechatSessionClient {

    private static final Logger log = LoggerFactory.getLogger(WechatSessionClient.class);
    private static final TypeReference<Map<String, Object>> WECHAT_SESSION_BODY = new TypeReference<>() {
    };

    private final WebClient webClient;
    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;

    public WechatSessionClient(WebClient.Builder webClientBuilder,
                               AuthProperties authProperties,
                               ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.authProperties = authProperties;
        this.objectMapper = objectMapper;
    }

    public Mono<WechatSession> exchangeCode(String code) {
        String normalizedCode = normalizeRequired(code);
        AuthProperties.Wechat wechat = authProperties.getWechat();
        String codeFingerprint = sha256(normalizedCode).substring(0, 12);
        log.info(
                "[wechat] code2Session started, appIdConfigured={}, secretConfigured={}, codeFingerprint={}",
                !isBlank(wechat.getAppId()),
                !isBlank(wechat.getAppSecret()),
                codeFingerprint
        );
        if (isBlank(wechat.getAppId()) || isBlank(wechat.getAppSecret())) {
            return Mono.error(new WechatLoginException(
                    ApiErrorCode.INVALID_REQUEST,
                    "微信登录配置缺少 appId 或 appSecret。"
            ));
        }

        URI uri = UriComponentsBuilder.fromHttpUrl(wechat.getCode2SessionUrl())
                .queryParam("appid", wechat.getAppId())
                .queryParam("secret", wechat.getAppSecret())
                .queryParam("js_code", normalizedCode)
                .queryParam("grant_type", "authorization_code")
                .build(true)
                .toUri();

        return webClient.get()
                .uri(uri)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> {
                            log.info(
                                    "[wechat] code2Session http response received, status={}, bodyLength={}, codeFingerprint={}",
                                    response.statusCode().value(),
                                    body.length(),
                                    codeFingerprint
                            );
                            if (response.statusCode().isError()) {
                                return Mono.error(new WechatLoginException(
                                        ApiErrorCode.REMOTE_ERROR,
                                        "微信登录服务调用失败，HTTP " + response.statusCode().value()
                                ));
                            }
                            return toSession(parseBody(body));
                        }))
                .doOnSuccess(session -> log.info(
                        "[wechat] code2Session succeeded, openIdFingerprint={}, unionIdProvided={}, codeFingerprint={}",
                        sha256(session.openId()).substring(0, 12),
                        !isBlank(session.unionId()),
                        codeFingerprint
                ))
                .onErrorMap(WebClientRequestException.class, ex -> new WechatLoginException(
                        ApiErrorCode.REMOTE_ERROR,
                        "微信登录服务暂时不可用，请稍后重试。",
                        ex
                ))
                .onErrorMap(ex -> !(ex instanceof WechatLoginException), ex -> new WechatLoginException(
                        ApiErrorCode.REMOTE_ERROR,
                        "微信登录服务调用失败。",
                        ex
                ));
    }

    private Map<String, Object> parseBody(String body) {
        if (isBlank(body)) {
            log.warn("[wechat] code2Session response body is empty");
            throw new WechatLoginException(ApiErrorCode.REMOTE_ERROR, "微信登录服务响应为空。");
        }
        try {
            return objectMapper.readValue(body, WECHAT_SESSION_BODY);
        } catch (JsonProcessingException ex) {
            log.warn("[wechat] code2Session response body parse failed, bodyLength={}", body.length());
            throw new WechatLoginException(ApiErrorCode.REMOTE_ERROR, "微信登录服务响应格式不正确。", ex);
        }
    }

    private Mono<WechatSession> toSession(Map<String, Object> body) {
        String errorCode = readString(body.get("errcode"));
        if (!isBlank(errorCode) && !"0".equals(errorCode)) {
            String message = readString(body.get("errmsg"));
            log.warn("[wechat] code2Session returned error, errcode={}, errmsg={}", errorCode, message);
            return Mono.error(new WechatLoginException(
                    ApiErrorCode.INVALID_REQUEST,
                    "微信登录失败：" + normalizeWechatError(errorCode, message)
            ));
        }

        String openId = readString(body.get("openid"));
        if (isBlank(openId)) {
            log.warn("[wechat] code2Session response missing openid");
            return Mono.error(new WechatLoginException(
                    ApiErrorCode.REMOTE_ERROR,
                    "微信登录服务响应缺少 openid。"
            ));
        }

        return Mono.just(new WechatSession(
                openId,
                readString(body.get("unionid")),
                readString(body.get("session_key"))
        ));
    }

    private String normalizeRequired(String code) {
        if (isBlank(code)) {
            throw new IllegalArgumentException("微信登录 code 不能为空。");
        }
        return code.trim();
    }

    private String readString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String normalizeWechatError(String errorCode, String message) {
        if (isBlank(message)) {
            return errorCode;
        }
        return message + " (errcode=" + errorCode + ")";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable.", ex);
        }
    }

    public record WechatSession(String openId, String unionId, String sessionKey) {
    }
}
