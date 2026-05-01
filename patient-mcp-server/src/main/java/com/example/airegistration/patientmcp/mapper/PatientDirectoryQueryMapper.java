package com.example.airegistration.patientmcp.mapper;

import com.example.airegistration.patientmcp.entity.PatientBindingView;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PatientDirectoryQueryMapper {

    @Select("""
            SELECT COUNT(1)
            FROM platform_user
            WHERE user_id = #{userId}
              AND status = 'ACTIVE'
            """)
    long countActiveUser(@Param("userId") String userId);

    @Select("""
            SELECT
                p.patient_id AS patientId,
                b.user_id AS userId,
                p.patient_name AS name,
                p.id_type AS idType,
                p.id_number_masked AS maskedIdNumber,
                p.phone_masked AS maskedPhone,
                b.relation_code AS relationCode,
                b.is_default AS defaultPatient
            FROM user_patient_binding b
            JOIN patient_profile p ON p.patient_id = b.patient_id
            JOIN platform_user u ON u.user_id = b.user_id
            WHERE b.user_id = #{userId}
              AND b.active = TRUE
              AND p.active = TRUE
              AND u.status = 'ACTIVE'
            ORDER BY b.is_default DESC, b.bound_at ASC
            LIMIT 1
            """)
    PatientBindingView selectDefaultByUserId(@Param("userId") String userId);

    @Select("""
            SELECT
                p.patient_id AS patientId,
                b.user_id AS userId,
                p.patient_name AS name,
                p.id_type AS idType,
                p.id_number_masked AS maskedIdNumber,
                p.phone_masked AS maskedPhone,
                b.relation_code AS relationCode,
                b.is_default AS defaultPatient
            FROM user_patient_binding b
            JOIN patient_profile p ON p.patient_id = b.patient_id
            JOIN platform_user u ON u.user_id = b.user_id
            WHERE b.user_id = #{userId}
              AND b.active = TRUE
              AND p.active = TRUE
              AND u.status = 'ACTIVE'
            ORDER BY b.is_default DESC, b.bound_at ASC
            """)
    List<PatientBindingView> selectByUserId(@Param("userId") String userId);

    @Select("""
            SELECT
                p.patient_id AS patientId,
                b.user_id AS userId,
                p.patient_name AS name,
                p.id_type AS idType,
                p.id_number_masked AS maskedIdNumber,
                p.phone_masked AS maskedPhone,
                b.relation_code AS relationCode,
                b.is_default AS defaultPatient
            FROM user_patient_binding b
            JOIN patient_profile p ON p.patient_id = b.patient_id
            WHERE b.user_id = #{userId}
              AND b.patient_id = #{patientId}
              AND b.active = TRUE
              AND p.active = TRUE
            LIMIT 1
            """)
    PatientBindingView selectBoundPatient(@Param("userId") String userId, @Param("patientId") String patientId);
}
