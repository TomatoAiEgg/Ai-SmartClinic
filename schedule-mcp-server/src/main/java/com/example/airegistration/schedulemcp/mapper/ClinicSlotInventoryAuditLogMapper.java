package com.example.airegistration.schedulemcp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.airegistration.schedulemcp.entity.ClinicSlotInventoryAuditLogEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ClinicSlotInventoryAuditLogMapper extends BaseMapper<ClinicSlotInventoryAuditLogEntity> {

    @Insert("""
            INSERT INTO clinic_slot_inventory_audit_log (
                operation_type,
                trace_id,
                department_code,
                doctor_id,
                clinic_date,
                start_time,
                operation_id,
                operation_source,
                success,
                reason,
                remaining_before,
                remaining_after,
                source_service
            )
            VALUES (
                #{operationType},
                #{traceId},
                #{departmentCode},
                #{doctorId},
                CAST(#{clinicDate} AS date),
                CAST(#{startTime} AS time),
                #{operationId},
                #{operationSource},
                #{success},
                #{reason},
                #{remainingBefore},
                #{remainingAfter},
                #{sourceService}
            )
            """)
    int insertAudit(ClinicSlotInventoryAuditLogEntity entity);
}
