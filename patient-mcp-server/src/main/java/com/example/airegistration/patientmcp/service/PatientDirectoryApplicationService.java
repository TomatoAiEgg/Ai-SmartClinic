package com.example.airegistration.patientmcp.service;

import com.example.airegistration.dto.PatientCreateRequest;
import com.example.airegistration.dto.PatientSummary;
import com.example.airegistration.enums.ApiErrorCode;
import com.example.airegistration.patientmcp.exception.PatientOperationException;
import com.example.airegistration.patientmcp.repository.PatientDirectoryRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PatientDirectoryApplicationService implements PatientDirectoryUseCase {

    private static final Set<String> RELATION_CODES = Set.of("SELF", "CHILD", "PARENT", "SPOUSE", "OTHER");

    private final PatientDirectoryRepository patientDirectoryRepository;

    public PatientDirectoryApplicationService(PatientDirectoryRepository patientDirectoryRepository) {
        this.patientDirectoryRepository = patientDirectoryRepository;
    }

    @Override
    public PatientSummary getDefaultPatient(String userId) {
        String normalizedUserId = normalizeUserId(userId);
        return patientDirectoryRepository.findDefaultByUserId(normalizedUserId);
    }

    @Override
    public List<PatientSummary> listPatients(String userId) {
        String normalizedUserId = normalizeUserId(userId);
        return patientDirectoryRepository.findByUserId(normalizedUserId);
    }

    @Override
    @Transactional
    public PatientSummary createPatient(PatientCreateRequest request) {
        if (request == null) {
            throw new PatientOperationException(
                    ApiErrorCode.INVALID_REQUEST,
                    "就诊人请求不能为空。",
                    Map.of("field", "request")
            );
        }
        String userId = normalizeUserId(request.userId());
        String name = normalizeRequired("name", request.name(), "就诊人姓名不能为空。");
        String idType = normalizeOptional(request.idType());
        String relationCode = normalizeRelationCode(request.relationCode());
        return patientDirectoryRepository.createForUser(new PatientCreateRequest(
                userId,
                name,
                idType == null ? "ID_CARD" : idType,
                normalizeOptional(request.idNumber()),
                normalizeOptional(request.phone()),
                relationCode,
                request.defaultPatient()
        ));
    }

    @Override
    @Transactional
    public PatientSummary setDefaultPatient(String userId, String patientId) {
        return patientDirectoryRepository.setDefault(
                normalizeUserId(userId),
                normalizeRequired("patientId", patientId, "patientId 不能为空。")
        );
    }

    private String normalizeUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new PatientOperationException(
                    ApiErrorCode.INVALID_REQUEST,
                    "userId 不能为空。",
                    Map.of("field", "userId")
            );
        }
        return userId.trim();
    }

    private String normalizeRequired(String field, String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new PatientOperationException(
                    ApiErrorCode.INVALID_REQUEST,
                    message,
                    Map.of("field", field)
            );
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeRelationCode(String value) {
        String relationCode = StringUtils.hasText(value) ? value.trim().toUpperCase() : "SELF";
        if (!RELATION_CODES.contains(relationCode)) {
            throw new PatientOperationException(
                    ApiErrorCode.INVALID_REQUEST,
                    "不支持当前就诊人关系类型。",
                    Map.of("field", "relationCode", "relationCode", relationCode)
            );
        }
        return relationCode;
    }
}
