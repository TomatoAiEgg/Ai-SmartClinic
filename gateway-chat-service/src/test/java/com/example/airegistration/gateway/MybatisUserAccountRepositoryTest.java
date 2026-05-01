package com.example.airegistration.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.airegistration.gateway.entity.PlatformUserEntity;
import com.example.airegistration.gateway.entity.UserLoginIdentityEntity;
import com.example.airegistration.gateway.mapper.PlatformUserMapper;
import com.example.airegistration.gateway.mapper.UserLoginIdentityMapper;
import com.example.airegistration.gateway.repository.MybatisUserAccountRepository;
import org.junit.jupiter.api.Test;

class MybatisUserAccountRepositoryTest {

    @Test
    void shouldReuseExistingUserIdFromIdentityAndUpsertBothTables() {
        PlatformUserMapper platformUserMapper = mock(PlatformUserMapper.class);
        UserLoginIdentityMapper userLoginIdentityMapper = mock(UserLoginIdentityMapper.class);
        when(userLoginIdentityMapper.selectActiveUserIdByProviderAndSubject("WECHAT_MINIAPP", "open-1"))
                .thenReturn("user-1");
        when(platformUserMapper.upsert(any(PlatformUserEntity.class))).thenReturn(1);
        when(userLoginIdentityMapper.upsert(any(UserLoginIdentityEntity.class))).thenReturn(1);

        MybatisUserAccountRepository repository = new MybatisUserAccountRepository(
                platformUserMapper,
                userLoginIdentityMapper
        );

        String userId = repository.upsertWechatUser(" open-1 ", " union-1 ", " Alice ", " https://img.test/a.png ");

        assertThat(userId).isEqualTo("user-1");
        verify(platformUserMapper, never()).selectActiveUserIdByOpenId(any());
        verify(platformUserMapper, never()).selectActiveUserIdByUnionId(any());
        verify(platformUserMapper).upsert(any(PlatformUserEntity.class));
        verify(userLoginIdentityMapper).upsert(any(UserLoginIdentityEntity.class));
    }

    @Test
    void shouldAllocateNewUserIdWhenNoExistingIdentityMatches() {
        PlatformUserMapper platformUserMapper = mock(PlatformUserMapper.class);
        UserLoginIdentityMapper userLoginIdentityMapper = mock(UserLoginIdentityMapper.class);
        when(userLoginIdentityMapper.selectActiveUserIdByProviderAndSubject("WECHAT_MINIAPP", "open-1"))
                .thenReturn(null);
        when(platformUserMapper.selectActiveUserIdByOpenId("open-1")).thenReturn(null);
        when(platformUserMapper.selectActiveUserIdByUnionId("union-1")).thenReturn(null);
        when(platformUserMapper.upsert(any(PlatformUserEntity.class))).thenReturn(1);
        when(userLoginIdentityMapper.upsert(any(UserLoginIdentityEntity.class))).thenReturn(1);

        MybatisUserAccountRepository repository = new MybatisUserAccountRepository(
                platformUserMapper,
                userLoginIdentityMapper
        );

        String userId = repository.upsertWechatUser("open-1", "union-1", null, null);

        assertThat(userId).startsWith("user-");
        verify(platformUserMapper).selectActiveUserIdByOpenId("open-1");
        verify(platformUserMapper).selectActiveUserIdByUnionId("union-1");
        verify(platformUserMapper).upsert(any(PlatformUserEntity.class));
        verify(userLoginIdentityMapper).upsert(any(UserLoginIdentityEntity.class));
    }

    @Test
    void shouldRejectBlankOpenId() {
        MybatisUserAccountRepository repository = new MybatisUserAccountRepository(
                mock(PlatformUserMapper.class),
                mock(UserLoginIdentityMapper.class)
        );

        assertThatThrownBy(() -> repository.upsertWechatUser(" ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("openId must not be blank.");
    }
}
