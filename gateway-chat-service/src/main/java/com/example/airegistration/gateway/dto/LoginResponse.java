package com.example.airegistration.gateway.dto;

public record LoginResponse(String token, String userId, long expiresIn, String tokenHeader) {
}
