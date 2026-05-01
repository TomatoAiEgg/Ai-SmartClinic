package com.example.airegistration.gateway.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.airegistration.gateway.entity.PlatformUserEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PlatformUserMapper extends BaseMapper<PlatformUserEntity> {

    @Select("""
            SELECT user_id
            FROM platform_user
            WHERE open_id = #{openId}
              AND status = 'ACTIVE'
            LIMIT 1
            """)
    String selectActiveUserIdByOpenId(String openId);

    @Select("""
            SELECT user_id
            FROM platform_user
            WHERE union_id = #{unionId}
              AND status = 'ACTIVE'
            ORDER BY last_login_at DESC NULLS LAST, created_at ASC
            LIMIT 1
            """)
    String selectActiveUserIdByUnionId(String unionId);

    @Insert("""
            INSERT INTO platform_user AS platform_user (
                user_id,
                open_id,
                union_id,
                nickname,
                display_name,
                avatar_url,
                source_channel,
                status,
                last_login_at
            )
            VALUES (
                #{userId},
                #{openId},
                #{unionId},
                #{nickname},
                #{displayName},
                #{avatarUrl},
                #{sourceChannel},
                'ACTIVE',
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (user_id) DO UPDATE SET
                open_id = COALESCE(EXCLUDED.open_id, platform_user.open_id),
                union_id = COALESCE(EXCLUDED.union_id, platform_user.union_id),
                nickname = COALESCE(EXCLUDED.nickname, platform_user.nickname),
                display_name = COALESCE(EXCLUDED.display_name, platform_user.display_name),
                avatar_url = COALESCE(EXCLUDED.avatar_url, platform_user.avatar_url),
                source_channel = EXCLUDED.source_channel,
                status = 'ACTIVE',
                last_login_at = CURRENT_TIMESTAMP
            """)
    int upsert(PlatformUserEntity entity);
}
