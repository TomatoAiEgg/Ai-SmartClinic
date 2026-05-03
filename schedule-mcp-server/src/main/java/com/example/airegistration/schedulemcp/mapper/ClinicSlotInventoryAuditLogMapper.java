package com.example.airegistration.schedulemcp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.airegistration.schedulemcp.entity.ClinicSlotInventoryAuditLogEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    @Select("""
            SELECT COUNT(1)
            FROM clinic_slot_inventory_audit_log
            WHERE operation_type = #{operationType}
              AND operation_id = #{operationId}
              AND operation_source = #{operationSource}
              AND success = TRUE
            """)
    long countSuccessfulOperation(@Param("operationType") String operationType,
                                  @Param("operationId") String operationId,
                                  @Param("operationSource") String operationSource);
}
