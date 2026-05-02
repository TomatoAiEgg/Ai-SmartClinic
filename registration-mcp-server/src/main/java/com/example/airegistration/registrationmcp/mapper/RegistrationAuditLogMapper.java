package com.example.airegistration.registrationmcp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.airegistration.registrationmcp.entity.RegistrationAuditLogEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

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
}
