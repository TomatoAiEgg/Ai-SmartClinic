package com.example.airegistration.gateway.repository;

public interface UserAccountRepository {

    String upsertWechatUser(String openId, String unionId, String nickname, String avatarUrl);
}
