package com.example.airegistration.dto;

public record SlotSummary(String departmentCode, String departmentName, String doctorId, String doctorName, String clinicDate, String startTime, int remainingSlots) {
}
