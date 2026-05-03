package com.example.airegistration.schedulemcp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.airegistration.schedulemcp.entity.ClinicSlotInventoryAuditLogEntity;
import java.util.List;
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

    @Select("""
            <script>
            SELECT
                audit_id,
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
                source_service,
                created_at
            FROM clinic_slot_inventory_audit_log
            WHERE 1 = 1
            <if test="operationId != null and operationId != ''">
              AND operation_id = #{operationId}
            </if>
            <if test="traceId != null and traceId != ''">
              AND trace_id = #{traceId}
            </if>
            <if test="departmentCode != null and departmentCode != ''">
              AND department_code = #{departmentCode}
            </if>
            <if test="doctorId != null and doctorId != ''">
              AND doctor_id = #{doctorId}
            </if>
            <if test="clinicDate != null and clinicDate != ''">
              AND clinic_date = CAST(#{clinicDate} AS date)
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
    List<ClinicSlotInventoryAuditLogEntity> selectAuditLogs(@Param("operationId") String operationId,
                                                            @Param("traceId") String traceId,
                                                            @Param("departmentCode") String departmentCode,
                                                            @Param("doctorId") String doctorId,
                                                            @Param("clinicDate") String clinicDate,
                                                            @Param("operationType") String operationType,
                                                            @Param("success") Boolean success,
                                                            @Param("limit") int limit);
}
