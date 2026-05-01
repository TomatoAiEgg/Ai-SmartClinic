package com.example.airegistration.gateway.mapper;

import com.example.airegistration.gateway.entity.UserLoginIdentityEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserLoginIdentityMapper {

    @Select("""
            SELECT user_id
            FROM user_login_identity
            WHERE provider_code = #{providerCode}
              AND provider_subject = #{providerSubject}
              AND status = 'ACTIVE'
            LIMIT 1
            """)
    String selectActiveUserIdByProviderAndSubject(@Param("providerCode") String providerCode,
                                                  @Param("providerSubject") String providerSubject);

    @Insert("""
            INSERT INTO user_login_identity AS user_login_identity (
                user_id,
                provider_code,
                provider_subject,
                union_id,
                status,
                last_login_at
            )
            VALUES (
                #{userId},
                #{providerCode},
                #{providerSubject},
                #{unionId},
                'ACTIVE',
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (provider_code, provider_subject) DO UPDATE SET
                user_id = EXCLUDED.user_id,
                union_id = COALESCE(EXCLUDED.union_id, user_login_identity.union_id),
                status = 'ACTIVE',
                last_login_at = CURRENT_TIMESTAMP
            """)
    int upsert(UserLoginIdentityEntity entity);
}
