package com.example.airegistration.schedulemcp;

import com.example.airegistration.domain.ScheduleSlotRequest;
import com.example.airegistration.domain.SlotSummary;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class ScheduleMcpTools {

    private final ScheduleCatalogService scheduleCatalogService;

    public ScheduleMcpTools(ScheduleCatalogService scheduleCatalogService) {
        this.scheduleCatalogService = scheduleCatalogService;
    }

    @Tool(name = "schedule-recommend-slot", description = "Return one recommended appointment slot for a department.")
    public String recommendSlot(@ToolParam(description = "Department code such as RESP or GEN.") String departmentCode) {
        SlotSummary slot = scheduleCatalogService.recommend(departmentCode);
        return "%s|%s|%s|%s %s|remaining=%d".formatted(slot.departmentCode(), slot.departmentName(), slot.doctorName(), slot.clinicDate(), slot.startTime(), slot.remainingSlots());
    }

    @Tool(name = "schedule-search-slots", description = "Search slots by department or doctor keyword.")
    public String searchSlots(@ToolParam(description = "Department or doctor keyword.") String keyword) {
        List<SlotSummary> slots = scheduleCatalogService.search(keyword);
        return "matched_slots=" + slots.size();
    }

    @Tool(name = "schedule-resolve-slot", description = "Resolve one specific slot by department, doctor, clinic date and start time.")
    public String resolveSlot(
            @ToolParam(description = "Department code.") String departmentCode,
            @ToolParam(description = "Doctor ID.") String doctorId,
            @ToolParam(description = "Clinic date.") String clinicDate,
            @ToolParam(description = "Start time.") String startTime) {
        SlotSummary slot = scheduleCatalogService.resolve(new ScheduleSlotRequest(departmentCode, doctorId, clinicDate, startTime));
        return "%s|%s|%s|%s %s|remaining=%d".formatted(slot.departmentCode(), slot.departmentName(), slot.doctorName(), slot.clinicDate(), slot.startTime(), slot.remainingSlots());
    }

    @Tool(name = "schedule-reserve-slot", description = "Reserve one specific slot before creating or rescheduling a registration.")
    public String reserveSlot(
            @ToolParam(description = "Department code.") String departmentCode,
            @ToolParam(description = "Doctor ID.") String doctorId,
            @ToolParam(description = "Clinic date.") String clinicDate,
            @ToolParam(description = "Start time.") String startTime) {
        SlotSummary slot = scheduleCatalogService.reserve(new ScheduleSlotRequest(departmentCode, doctorId, clinicDate, startTime));
        return "%s|%s|%s|%s %s|remaining=%d".formatted(slot.departmentCode(), slot.departmentName(), slot.doctorName(), slot.clinicDate(), slot.startTime(), slot.remainingSlots());
    }

    @Tool(name = "schedule-release-slot", description = "Release one specific slot after cancellation or rollback.")
    public String releaseSlot(
            @ToolParam(description = "Department code.") String departmentCode,
            @ToolParam(description = "Doctor ID.") String doctorId,
            @ToolParam(description = "Clinic date.") String clinicDate,
            @ToolParam(description = "Start time.") String startTime) {
        SlotSummary slot = scheduleCatalogService.release(new ScheduleSlotRequest(departmentCode, doctorId, clinicDate, startTime));
        return "%s|%s|%s|%s %s|remaining=%d".formatted(slot.departmentCode(), slot.departmentName(), slot.doctorName(), slot.clinicDate(), slot.startTime(), slot.remainingSlots());
    }
}
