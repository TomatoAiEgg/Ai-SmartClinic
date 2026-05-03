package com.example.airegistration.registrationmcp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.airegistration.registrationmcp.entity.RegistrationAuditLogEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RegistrationAuditLogMapper extends BaseMapper<RegistrationAuditLogEntity> {

    @Insert("""
            INSERT INTO registration_audit_log (
                registration_id,
                operation_type,
                operator_user_id,
                chat_id,
                source_service,
                success,
                reason,
                trace_id,
                request_payload,
                response_payload,
                before_snapshot,
                after_snapshot
            )
            VALUES (
                #{registrationId},
                #{operationType},
                #{operatorUserId},
                #{chatId},
                #{sourceService},
                #{success},
                #{reason},
                #{traceId},
                CAST(#{requestPayload} AS jsonb),
                CAST(#{responsePayload} AS jsonb),
                CAST(#{beforeSnapshot} AS jsonb),
                CAST(#{afterSnapshot} AS jsonb)
            )
            """)
    int insertAudit(RegistrationAuditLogEntity entity);

    @Select("""
            <script>
            SELECT
                audit_id,
                registration_id,
                operation_type,
                operator_user_id,
                chat_id,
                source_service,
                success,
                reason,
                trace_id,
                request_payload::text AS request_payload,
                response_payload::text AS response_payload,
                before_snapshot::text AS before_snapshot,
                after_snapshot::text AS after_snapshot,
                created_at
            FROM registration_audit_log
            WHERE 1 = 1
            <if test="registrationId != null and registrationId != ''">
              AND registration_id = #{registrationId}
            </if>
            <if test="operatorUserId != null and operatorUserId != ''">
              AND operator_user_id = #{operatorUserId}
            </if>
            <if test="chatId != null and chatId != ''">
              AND chat_id = #{chatId}
            </if>
            <if test="traceId != null and traceId != ''">
              AND trace_id = #{traceId}
            </if>
            <if test="operationType != null and operationType != ''">
              AND operation_type = #{operationType}
            </if>
            <if test="success != null">
              AND success = #{success}
            </if>
            ORDER BY audit_id DESC
            LIMIT #{limit}
            </script>
            """)
    List<RegistrationAuditLogEntity> selectAuditLogs(@Param("registrationId") String registrationId,
                                                     @Param("operatorUserId") String operatorUserId,
                                                     @Param("chatId") String chatId,
                                                     @Param("traceId") String traceId,
                                                     @Param("operationType") String operationType,
                                                     @Param("success") Boolean success,
                                                     @Param("limit") int limit);
}
