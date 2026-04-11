package com.example.airegistration.domain;

public record ScheduleSlotRequest(
        String departmentCode,
        String doctorId,
        String clinicDate,
        String startTime
) {
}
