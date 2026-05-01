package com.example.airegistration.dto;

public record ScheduleSlotRequest(
        String departmentCode,
        String doctorId,
        String clinicDate,
        String startTime
) {
}
