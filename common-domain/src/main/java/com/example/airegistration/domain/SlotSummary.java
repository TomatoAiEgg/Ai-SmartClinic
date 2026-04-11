package com.example.airegistration.domain;

public record SlotSummary(String departmentCode, String departmentName, String doctorId, String doctorName, String clinicDate, String startTime, int remainingSlots) {
}
