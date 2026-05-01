package com.example.airegistration.schedulemcp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.airegistration.schedulemcp.entity.ClinicSlotEntity;
import com.example.airegistration.schedulemcp.entity.ScheduleSlotView;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ClinicSlotMapper extends BaseMapper<ClinicSlotEntity> {

    @Select("""
            SELECT
                s.department_code AS departmentCode,
                d.department_name AS departmentName,
                s.doctor_id AS doctorId,
                doc.doctor_name AS doctorName,
                s.clinic_date AS clinicDate,
                s.start_time AS startTime,
                s.capacity AS capacity,
                s.remaining_slots AS remainingSlots
            FROM clinic_slot s
            JOIN department d ON d.department_code = s.department_code
            JOIN doctor doc ON doc.doctor_id = s.doctor_id
            WHERE s.status = 'OPEN'
              AND s.clinic_date >= CURRENT_DATE
              AND d.active = TRUE
              AND d.online_enabled = TRUE
              AND doc.active = TRUE
            ORDER BY s.clinic_date, s.start_time, s.department_code
            """)
    List<ScheduleSlotView> selectActiveSlots();

    @Select("""
            SELECT
                s.department_code AS departmentCode,
                d.department_name AS departmentName,
                s.doctor_id AS doctorId,
                doc.doctor_name AS doctorName,
                s.clinic_date AS clinicDate,
                s.start_time AS startTime,
                s.capacity AS capacity,
                s.remaining_slots AS remainingSlots
            FROM clinic_slot s
            JOIN department d ON d.department_code = s.department_code
            JOIN doctor doc ON doc.doctor_id = s.doctor_id
            WHERE s.department_code = #{departmentCode}
              AND s.doctor_id = #{doctorId}
              AND s.clinic_date = #{clinicDate}
              AND s.clinic_date >= CURRENT_DATE
              AND s.start_time = #{startTime}
              AND s.status = 'OPEN'
            LIMIT 1
            """)
    ScheduleSlotView selectActiveSlot(@Param("departmentCode") String departmentCode,
                                      @Param("doctorId") String doctorId,
                                      @Param("clinicDate") LocalDate clinicDate,
                                      @Param("startTime") LocalTime startTime);

    @Select("""
            WITH updated AS (
                UPDATE clinic_slot
                SET remaining_slots = remaining_slots - 1
                WHERE department_code = #{departmentCode}
                  AND doctor_id = #{doctorId}
                  AND clinic_date = #{clinicDate}
                  AND clinic_date >= CURRENT_DATE
                  AND start_time = #{startTime}
                  AND status = 'OPEN'
                  AND remaining_slots > 0
                RETURNING department_code, doctor_id, clinic_date, start_time, capacity, remaining_slots
            )
            SELECT
                updated.department_code AS departmentCode,
                d.department_name AS departmentName,
                updated.doctor_id AS doctorId,
                doc.doctor_name AS doctorName,
                updated.clinic_date AS clinicDate,
                updated.start_time AS startTime,
                updated.capacity AS capacity,
                updated.remaining_slots AS remainingSlots
            FROM updated
            JOIN department d ON d.department_code = updated.department_code
            JOIN doctor doc ON doc.doctor_id = updated.doctor_id
            """)
    ScheduleSlotView reserveSlot(@Param("departmentCode") String departmentCode,
                                 @Param("doctorId") String doctorId,
                                 @Param("clinicDate") LocalDate clinicDate,
                                 @Param("startTime") LocalTime startTime);

    @Select("""
            WITH updated AS (
                UPDATE clinic_slot
                SET remaining_slots = LEAST(capacity, remaining_slots + 1)
                WHERE department_code = #{departmentCode}
                  AND doctor_id = #{doctorId}
                  AND clinic_date = #{clinicDate}
                  AND start_time = #{startTime}
                  AND status = 'OPEN'
                RETURNING department_code, doctor_id, clinic_date, start_time, capacity, remaining_slots
            )
            SELECT
                updated.department_code AS departmentCode,
                d.department_name AS departmentName,
                updated.doctor_id AS doctorId,
                doc.doctor_name AS doctorName,
                updated.clinic_date AS clinicDate,
                updated.start_time AS startTime,
                updated.capacity AS capacity,
                updated.remaining_slots AS remainingSlots
            FROM updated
            JOIN department d ON d.department_code = updated.department_code
            JOIN doctor doc ON doc.doctor_id = updated.doctor_id
            """)
    ScheduleSlotView releaseSlot(@Param("departmentCode") String departmentCode,
                                 @Param("doctorId") String doctorId,
                                 @Param("clinicDate") LocalDate clinicDate,
                                 @Param("startTime") LocalTime startTime);
}
