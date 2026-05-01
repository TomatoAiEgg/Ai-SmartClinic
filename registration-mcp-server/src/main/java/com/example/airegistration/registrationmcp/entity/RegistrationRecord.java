package com.example.airegistration.registrationmcp.entity;

import com.example.airegistration.dto.RegistrationResult;
import com.example.airegistration.enums.RegistrationStatus;

public class RegistrationRecord {

    private final String registrationId;
    private final String userId;
    private final String patientId;
    private final String departmentCode;
    private final String doctorId;
    private final String externalRequestId;
    private final String chatId;
    private String clinicDate;
    private String startTime;
    private RegistrationStatus status;

    public RegistrationRecord(String registrationId,
                              String userId,
                              String patientId,
                              String departmentCode,
                              String doctorId,
                              String clinicDate,
                              String startTime,
                              RegistrationStatus status) {
        this(registrationId, userId, patientId, departmentCode, doctorId, clinicDate, startTime, status, null, null);
    }

    public RegistrationRecord(String registrationId,
                              String userId,
                              String patientId,
                              String departmentCode,
                              String doctorId,
                              String clinicDate,
                              String startTime,
                              RegistrationStatus status,
                              String externalRequestId,
                              String chatId) {
        this.registrationId = registrationId;
        this.userId = userId;
        this.patientId = patientId;
        this.departmentCode = departmentCode;
        this.doctorId = doctorId;
        this.clinicDate = clinicDate;
        this.startTime = startTime;
        this.status = status;
        this.externalRequestId = externalRequestId;
        this.chatId = chatId;
    }

    public void cancel() {
        this.status = RegistrationStatus.CANCELLED;
    }

    public void reschedule(String clinicDate, String startTime) {
        this.clinicDate = clinicDate;
        this.startTime = startTime;
        this.status = RegistrationStatus.RESCHEDULED;
    }

    public RegistrationResult toResult(String message) {
        return new RegistrationResult(
                registrationId,
                status.code(),
                message,
                patientId,
                departmentCode,
                doctorId,
                clinicDate,
                startTime
        );
    }

    public String registrationId() {
        return registrationId;
    }

    public String userId() {
        return userId;
    }

    public String patientId() {
        return patientId;
    }

    public String departmentCode() {
        return departmentCode;
    }

    public String doctorId() {
        return doctorId;
    }

    public String clinicDate() {
        return clinicDate;
    }

    public String startTime() {
        return startTime;
    }

    public RegistrationStatus status() {
        return status;
    }

    public String externalRequestId() {
        return externalRequestId;
    }

    public String chatId() {
        return chatId;
    }
}
