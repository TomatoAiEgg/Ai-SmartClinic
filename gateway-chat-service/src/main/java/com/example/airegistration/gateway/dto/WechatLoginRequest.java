package com.example.airegistration.gateway.dto;

public record WechatLoginRequest(String code, String nickname, String avatarUrl) {
}
