package com.example.airegistration.dto;

public record RegistrationCommand(
        String userId,
        String patientId,
        String departmentCode,
        String doctorId,
        String clinicDate,
        String startTime,
        boolean confirmed,
        String externalRequestId,
        String chatId
) {
    public RegistrationCommand(String userId,
                               String patientId,
                               String departmentCode,
                               String doctorId,
                               String clinicDate,
                               String startTime,
                               boolean confirmed) {
        this(userId, patientId, departmentCode, doctorId, clinicDate, startTime, confirmed, null, null);
    }
}
