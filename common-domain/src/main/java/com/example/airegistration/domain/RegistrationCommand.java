package com.example.airegistration.domain;

public record RegistrationCommand(String userId, String patientId, String departmentCode, String doctorId, String clinicDate, String startTime, boolean confirmed) {
}
