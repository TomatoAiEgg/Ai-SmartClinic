package com.example.airegistration.dto;

public record ScheduleSlotRequest(
        String departmentCode,
        String doctorId,
        String clinicDate,
        String startTime,
        String operationId,
        String operationSource
) {

    public ScheduleSlotRequest(String departmentCode,
                               String doctorId,
                               String clinicDate,
                               String startTime) {
        this(departmentCode, doctorId, clinicDate, startTime, null, null);
    }

    public ScheduleSlotRequest withOperation(String operationId, String operationSource) {
        return new ScheduleSlotRequest(departmentCode, doctorId, clinicDate, startTime, operationId, operationSource);
    }
}
