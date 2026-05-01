package com.example.airegistration.registrationmcp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.airegistration.registrationmcp.entity.RegistrationOrderEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RegistrationOrderMapper extends BaseMapper<RegistrationOrderEntity> {

    @Insert("""
            INSERT INTO registration_order AS registration_order (
                registration_id,
                user_id,
                patient_id,
                slot_id,
                department_code,
                doctor_id,
                clinic_date,
                start_time,
                status,
                confirmation_required,
                source_channel,
                chat_id,
                external_request_id,
                confirmed_at,
                cancelled_at
            )
            VALUES (
                #{registrationId},
                #{userId},
                #{patientId},
                (
                    SELECT slot_id
                    FROM clinic_slot
                    WHERE department_code = #{departmentCode}
                      AND doctor_id = #{doctorId}
                      AND clinic_date = #{clinicDate}
                      AND start_time = #{startTime}
                    LIMIT 1
                ),
                #{departmentCode},
                #{doctorId},
                #{clinicDate},
                #{startTime},
                #{status},
                #{confirmationRequired},
                #{sourceChannel},
                #{chatId},
                #{externalRequestId},
                CURRENT_TIMESTAMP,
                #{cancelledAt}
            )
            ON CONFLICT (registration_id) DO UPDATE SET
                user_id = EXCLUDED.user_id,
                patient_id = EXCLUDED.patient_id,
                slot_id = EXCLUDED.slot_id,
                department_code = EXCLUDED.department_code,
                doctor_id = EXCLUDED.doctor_id,
                clinic_date = EXCLUDED.clinic_date,
                start_time = EXCLUDED.start_time,
                status = EXCLUDED.status,
                confirmation_required = EXCLUDED.confirmation_required,
                source_channel = EXCLUDED.source_channel,
                chat_id = COALESCE(EXCLUDED.chat_id, registration_order.chat_id),
                external_request_id = COALESCE(EXCLUDED.external_request_id, registration_order.external_request_id),
                confirmed_at = COALESCE(registration_order.confirmed_at, CURRENT_TIMESTAMP),
                cancelled_at = EXCLUDED.cancelled_at
            """)
    int upsert(RegistrationOrderEntity entity);

    @Select("""
            SELECT
                registration_id,
                user_id,
                patient_id,
                slot_id,
                department_code,
                doctor_id,
                clinic_date,
                start_time,
                status,
                confirmation_required,
                source_channel,
                chat_id,
                external_request_id,
                cancel_reason,
                confirmed_at,
                cancelled_at
            FROM registration_order
            WHERE registration_id = #{registrationId}
            """)
    RegistrationOrderEntity selectByRegistrationId(String registrationId);

    @Select("""
            SELECT
                registration_id,
                user_id,
                patient_id,
                slot_id,
                department_code,
                doctor_id,
                clinic_date,
                start_time,
                status,
                confirmation_required,
                source_channel,
                chat_id,
                external_request_id,
                cancel_reason,
                confirmed_at,
                cancelled_at
            FROM registration_order
            WHERE external_request_id = #{externalRequestId}
            """)
    RegistrationOrderEntity selectByExternalRequestId(String externalRequestId);

    @Select("""
            SELECT
                registration_id,
                user_id,
                patient_id,
                slot_id,
                department_code,
                doctor_id,
                clinic_date,
                start_time,
                status,
                confirmation_required,
                source_channel,
                chat_id,
                external_request_id,
                cancel_reason,
                confirmed_at,
                cancelled_at
            FROM registration_order
            ORDER BY clinic_date, start_time, registration_id
            """)
    List<RegistrationOrderEntity> selectAllOrders();
}
