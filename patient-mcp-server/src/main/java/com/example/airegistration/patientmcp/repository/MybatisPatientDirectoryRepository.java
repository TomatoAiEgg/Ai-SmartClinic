package com.example.airegistration.patientmcp.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.airegistration.dto.PatientCreateRequest;
import com.example.airegistration.dto.PatientSummary;
import com.example.airegistration.enums.ApiErrorCode;
import com.example.airegistration.patientmcp.entity.PatientBindingView;
import com.example.airegistration.patientmcp.entity.PatientProfileEntity;
import com.example.airegistration.patientmcp.entity.UserPatientBindingEntity;
import com.example.airegistration.patientmcp.exception.PatientOperationException;
import com.example.airegistration.patientmcp.mapper.PatientDirectoryQueryMapper;
import com.example.airegistration.patientmcp.mapper.PatientProfileMapper;
import com.example.airegistration.patientmcp.mapper.UserPatientBindingMapper;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisPatientDirectoryRepository implements PatientDirectoryRepository {

    private final PatientProfileMapper patientProfileMapper;
    private final UserPatientBindingMapper userPatientBindingMapper;
    private final PatientDirectoryQueryMapper patientDirectoryQueryMapper;

    public MybatisPatientDirectoryRepository(PatientProfileMapper patientProfileMapper,
                                            UserPatientBindingMapper userPatientBindingMapper,
                                            PatientDirectoryQueryMapper patientDirectoryQueryMapper) {
        this.patientProfileMapper = patientProfileMapper;
        this.userPatientBindingMapper = userPatientBindingMapper;
        this.patientDirectoryQueryMapper = patientDirectoryQueryMapper;
    }

    @Override
    public PatientSummary findDefaultByUserId(String userId) {
        return toRequiredSummary(
                patientDirectoryQueryMapper.selectDefaultByUserId(userId),
                "No active patient binding found for user.",
                Map.of("userId", userId)
        );
    }

    @Override
    public List<PatientSummary> findByUserId(String userId) {
        return patientDirectoryQueryMapper.selectByUserId(userId)
                .stream()
                .map(PatientBindingView::toSummary)
                .toList();
    }

    @Override
    public PatientSummary createForUser(PatientCreateRequest request) {
        ensureActiveUser(request.userId());
        boolean defaultPatient = request.defaultPatient() || !hasActiveBinding(request.userId());
        if (defaultPatient) {
            clearDefaultBinding(request.userId());
        }

        String patientId = nextPatientId();
        insertPatientProfile(patientId, request);
        insertBinding(request.userId(), patientId, request.relationCode(), defaultPatient);
        return toRequiredSummary(
                patientDirectoryQueryMapper.selectBoundPatient(request.userId(), patientId),
                "Patient binding does not exist.",
                Map.of("userId", request.userId(), "patientId", patientId)
        );
    }

    @Override
    public PatientSummary setDefault(String userId, String patientId) {
        PatientSummary patient = toRequiredSummary(
                patientDirectoryQueryMapper.selectBoundPatient(userId, patientId),
                "Patient binding does not exist.",
                Map.of("userId", userId, "patientId", patientId)
        );
        clearDefaultBinding(userId);
        int updated = userPatientBindingMapper.update(
                null,
                new LambdaUpdateWrapper<UserPatientBindingEntity>()
                        .eq(UserPatientBindingEntity::getUserId, userId)
                        .eq(UserPatientBindingEntity::getPatientId, patientId)
                        .eq(UserPatientBindingEntity::getActive, true)
                        .set(UserPatientBindingEntity::getDefaultPatient, true)
        );
        if (updated == 0) {
            throw new PatientOperationException(
                    ApiErrorCode.NOT_FOUND,
                    "Patient binding does not exist.",
                    Map.of("userId", userId, "patientId", patientId)
            );
        }
        return new PatientSummary(
                patient.patientId(),
                patient.userId(),
                patient.name(),
                patient.idType(),
                patient.maskedIdNumber(),
                patient.maskedPhone(),
                patient.relationCode(),
                true
        );
    }

    private void ensureActiveUser(String userId) {
        if (patientDirectoryQueryMapper.countActiveUser(userId) > 0) {
            return;
        }
        throw new PatientOperationException(
                ApiErrorCode.NOT_FOUND,
                "Active user does not exist.",
                Map.of("userId", userId)
        );
    }

    private boolean hasActiveBinding(String userId) {
        Long count = userPatientBindingMapper.selectCount(
                new LambdaQueryWrapper<UserPatientBindingEntity>()
                        .eq(UserPatientBindingEntity::getUserId, userId)
                        .eq(UserPatientBindingEntity::getActive, true)
        );
        return count != null && count > 0;
    }

    private void clearDefaultBinding(String userId) {
        userPatientBindingMapper.update(
                null,
                new LambdaUpdateWrapper<UserPatientBindingEntity>()
                        .eq(UserPatientBindingEntity::getUserId, userId)
                        .eq(UserPatientBindingEntity::getActive, true)
                        .eq(UserPatientBindingEntity::getDefaultPatient, true)
                        .set(UserPatientBindingEntity::getDefaultPatient, false)
        );
    }

    private void insertPatientProfile(String patientId, PatientCreateRequest request) {
        PatientProfileEntity entity = new PatientProfileEntity();
        entity.setPatientId(patientId);
        entity.setPatientName(request.name());
        entity.setIdType(normalizeCode(request.idType(), "ID_CARD"));
        entity.setIdNumberMasked(maskIdNumber(request.idNumber()));
        entity.setPhoneMasked(maskPhone(request.phone()));
        entity.setActive(true);
        entity.setVerifiedStatus("UNVERIFIED");
        entity.setSourceChannel("MINIAPP");
        if (patientProfileMapper.insert(entity) != 1) {
            throw new PatientOperationException(
                    ApiErrorCode.INTERNAL_ERROR,
                    "Failed to create patient profile.",
                    Map.of("patientId", patientId)
            );
        }
    }

    private void insertBinding(String userId, String patientId, String relationCode, boolean defaultPatient) {
        UserPatientBindingEntity entity = new UserPatientBindingEntity();
        entity.setUserId(userId);
        entity.setPatientId(patientId);
        entity.setRelationCode(normalizeCode(relationCode, "SELF"));
        entity.setDefaultPatient(defaultPatient);
        entity.setActive(true);
        if (userPatientBindingMapper.insert(entity) != 1) {
            throw new PatientOperationException(
                    ApiErrorCode.INTERNAL_ERROR,
                    "Failed to create patient binding.",
                    Map.of("userId", userId, "patientId", patientId)
            );
        }
    }

    private PatientSummary toRequiredSummary(PatientBindingView view, String message, Map<String, Object> details) {
        if (view != null) {
            return view.toSummary();
        }
        throw new PatientOperationException(ApiErrorCode.NOT_FOUND, message, details);
    }

    private String nextPatientId() {
        return "patient-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private String normalizeCode(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String maskIdNumber(String value) {
        return maskSensitive(value, 3, 4);
    }

    private String maskPhone(String value) {
        return maskSensitive(value, 3, 4);
    }

    private String maskSensitive(String value, int prefixLength, int suffixLength) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String text = value.trim();
        if (text.length() <= prefixLength + suffixLength) {
            return "*".repeat(text.length());
        }
        String prefix = text.substring(0, prefixLength);
        String suffix = text.substring(text.length() - suffixLength);
        int maskedLength = Math.max(4, text.length() - prefixLength - suffixLength);
        return prefix + "*".repeat(maskedLength) + suffix;
    }
}
