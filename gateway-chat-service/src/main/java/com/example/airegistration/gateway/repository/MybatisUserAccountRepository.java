package com.example.airegistration.gateway.repository;

import com.example.airegistration.gateway.entity.PlatformUserEntity;
import com.example.airegistration.gateway.entity.UserLoginIdentityEntity;
import com.example.airegistration.gateway.mapper.PlatformUserMapper;
import com.example.airegistration.gateway.mapper.UserLoginIdentityMapper;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MybatisUserAccountRepository implements UserAccountRepository {

    static final String PROVIDER_WECHAT_MINIAPP = "WECHAT_MINIAPP";

    private final PlatformUserMapper platformUserMapper;
    private final UserLoginIdentityMapper userLoginIdentityMapper;

    public MybatisUserAccountRepository(PlatformUserMapper platformUserMapper,
                                        UserLoginIdentityMapper userLoginIdentityMapper) {
        this.platformUserMapper = platformUserMapper;
        this.userLoginIdentityMapper = userLoginIdentityMapper;
    }

    @Override
    @Transactional
    public String upsertWechatUser(String openId, String unionId, String nickname, String avatarUrl) {
        String normalizedOpenId = normalizeRequired(openId, "openId");
        String normalizedUnionId = normalizeOptional(unionId);
        String normalizedNickname = normalizeOptional(nickname);
        String normalizedAvatarUrl = normalizeOptional(avatarUrl);

        String userId = resolveUserId(normalizedOpenId, normalizedUnionId);
        if (userId == null) {
            userId = nextUserId();
        }

        PlatformUserEntity platformUser = new PlatformUserEntity();
        platformUser.setUserId(userId);
        platformUser.setOpenId(normalizedOpenId);
        platformUser.setUnionId(normalizedUnionId);
        platformUser.setNickname(normalizedNickname);
        platformUser.setDisplayName(normalizedNickname);
        platformUser.setAvatarUrl(normalizedAvatarUrl);
        platformUser.setSourceChannel(PROVIDER_WECHAT_MINIAPP);

        UserLoginIdentityEntity identity = new UserLoginIdentityEntity();
        identity.setUserId(userId);
        identity.setProviderCode(PROVIDER_WECHAT_MINIAPP);
        identity.setProviderSubject(normalizedOpenId);
        identity.setUnionId(normalizedUnionId);

        ensureUpdated(platformUserMapper.upsert(platformUser), "platform_user");
        ensureUpdated(userLoginIdentityMapper.upsert(identity), "user_login_identity");
        return userId;
    }

    private String resolveUserId(String openId, String unionId) {
        String userId = userLoginIdentityMapper.selectActiveUserIdByProviderAndSubject(PROVIDER_WECHAT_MINIAPP, openId);
        if (userId != null) {
            return userId;
        }

        userId = platformUserMapper.selectActiveUserIdByOpenId(openId);
        if (userId != null || unionId == null) {
            return userId;
        }
        return platformUserMapper.selectActiveUserIdByUnionId(unionId);
    }

    private void ensureUpdated(int updated, String tableName) {
        if (updated < 1) {
            throw new IllegalStateException("Failed to upsert user account data into " + tableName + ".");
        }
    }

    private String nextUserId() {
        return "user-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private String normalizeRequired(String value, String field) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
