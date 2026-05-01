package com.example.airegistration.schedulemcp.entity;

import com.example.airegistration.dto.SlotSummary;
import com.example.airegistration.enums.ApiErrorCode;
import com.example.airegistration.schedulemcp.exception.ScheduleOperationException;
import java.util.Map;

public class ScheduleSlotInventory {

    private final String departmentCode;
    private final String departmentName;
    private final String doctorId;
    private final String doctorName;
    private final String clinicDate;
    private final String startTime;
    private final int capacity;
    private int remainingSlots;

    public ScheduleSlotInventory(String departmentCode,
                                 String departmentName,
                                 String doctorId,
                                 String doctorName,
                                 String clinicDate,
                                 String startTime,
                                 int capacity) {
        this(departmentCode, departmentName, doctorId, doctorName, clinicDate, startTime, capacity, capacity);
    }

    public ScheduleSlotInventory(String departmentCode,
                                 String departmentName,
                                 String doctorId,
                                 String doctorName,
                                 String clinicDate,
                                 String startTime,
                                 int capacity,
                                 int remainingSlots) {
        this.departmentCode = departmentCode;
        this.departmentName = departmentName;
        this.doctorId = doctorId;
        this.doctorName = doctorName;
        this.clinicDate = clinicDate;
        this.startTime = startTime;
        this.capacity = capacity;
        this.remainingSlots = Math.max(0, Math.min(remainingSlots, capacity));
    }

    public ScheduleSlotKey key() {
        return new ScheduleSlotKey(departmentCode, doctorId, clinicDate, startTime);
    }

    public synchronized SlotSummary reserve() {
        if (remainingSlots <= 0) {
            throw new ScheduleOperationException(
                    ApiErrorCode.NOT_FOUND,
                    "Slot has no remaining capacity.",
                    Map.of(
                            "departmentCode", departmentCode,
                            "doctorId", doctorId,
                            "clinicDate", clinicDate,
                            "startTime", startTime
                    )
            );
        }
        remainingSlots--;
        return toSummary();
    }

    public synchronized SlotSummary release() {
        if (remainingSlots < capacity) {
            remainingSlots++;
        }
        return toSummary();
    }

    public SlotSummary toSummary() {
        return new SlotSummary(
                departmentCode,
                departmentName,
                doctorId,
                doctorName,
                clinicDate,
                startTime,
                remainingSlots
        );
    }

    public boolean matchesKeyword(String keyword) {
        return departmentCode.toLowerCase().contains(keyword)
                || departmentName.toLowerCase().contains(keyword)
                || doctorId.toLowerCase().contains(keyword)
                || doctorName.toLowerCase().contains(keyword)
                || clinicDate.toLowerCase().contains(keyword)
                || startTime.toLowerCase().contains(keyword);
    }

    public String departmentCode() {
        return departmentCode;
    }

    public String clinicDate() {
        return clinicDate;
    }

    public String startTime() {
        return startTime;
    }

    public int remainingSlots() {
        return remainingSlots;
    }
}
