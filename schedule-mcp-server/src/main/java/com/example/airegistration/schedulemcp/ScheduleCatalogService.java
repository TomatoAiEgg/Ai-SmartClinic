package com.example.airegistration.schedulemcp;

import com.example.airegistration.domain.ScheduleSlotRequest;
import com.example.airegistration.domain.SlotSummary;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ScheduleCatalogService {

    private final Map<SlotKey, SlotInventory> slots = new ConcurrentHashMap<>();

    public ScheduleCatalogService() {
        seedSlots().forEach(slot -> slots.put(slot.key(), slot));
    }

    public SlotSummary recommend(String departmentCode) {
        String normalizedDepartmentCode = normalizeRequired("departmentCode", departmentCode).toUpperCase();
        return slots.values().stream()
                .filter(slot -> slot.departmentCode.equals(normalizedDepartmentCode))
                .filter(slot -> slot.remainingSlots > 0)
                .sorted(slotComparator())
                .findFirst()
                .map(SlotInventory::toSummary)
                .orElseThrow(() -> new ScheduleOperationException(
                        "NOT_FOUND",
                        "No available slot was found for the requested department.",
                        Map.of("departmentCode", normalizedDepartmentCode)
                ));
    }

    public List<SlotSummary> search(String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        return slots.values().stream()
                .filter(slot -> normalizedKeyword.isEmpty() || slot.matchesKeyword(normalizedKeyword))
                .sorted(slotComparator())
                .map(SlotInventory::toSummary)
                .toList();
    }

    public SlotSummary resolve(ScheduleSlotRequest request) {
        return getRequiredSlot(request).toSummary();
    }

    public SlotSummary reserve(ScheduleSlotRequest request) {
        SlotInventory slot = getRequiredSlot(request);
        synchronized (slot) {
            if (slot.remainingSlots <= 0) {
                throw new ScheduleOperationException(
                        "NOT_FOUND",
                        "No remaining slot is available for the requested schedule.",
                        Map.of(
                                "departmentCode", slot.departmentCode,
                                "doctorId", slot.doctorId,
                                "clinicDate", slot.clinicDate,
                                "startTime", slot.startTime
                        )
                );
            }
            slot.remainingSlots--;
            return slot.toSummary();
        }
    }

    public SlotSummary release(ScheduleSlotRequest request) {
        SlotInventory slot = getRequiredSlot(request);
        synchronized (slot) {
            if (slot.remainingSlots < slot.capacity) {
                slot.remainingSlots++;
            }
            return slot.toSummary();
        }
    }

    private SlotInventory getRequiredSlot(ScheduleSlotRequest request) {
        String departmentCode = normalizeRequired("departmentCode", request.departmentCode()).toUpperCase();
        String doctorId = normalizeRequired("doctorId", request.doctorId());
        String clinicDate = normalizeRequired("clinicDate", request.clinicDate());
        String startTime = normalizeRequired("startTime", request.startTime());
        SlotInventory slot = slots.get(new SlotKey(departmentCode, doctorId, clinicDate, startTime));
        if (slot == null) {
            throw new ScheduleOperationException(
                    "NOT_FOUND",
                    "The requested slot does not exist.",
                    Map.of(
                            "departmentCode", departmentCode,
                            "doctorId", doctorId,
                            "clinicDate", clinicDate,
                            "startTime", startTime
                    )
            );
        }
        return slot;
    }

    private Comparator<SlotInventory> slotComparator() {
        return Comparator.comparing((SlotInventory slot) -> slot.clinicDate)
                .thenComparing(slot -> slot.startTime)
                .thenComparing(slot -> slot.departmentCode);
    }

    private String normalizeRequired(String field, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new ScheduleOperationException(
                    "INVALID_REQUEST",
                    "Required field is missing: " + field,
                    Map.of("field", field)
            );
        }
        return value.trim();
    }

    private List<SlotInventory> seedSlots() {
        LocalDate today = LocalDate.now();
        return List.of(
                new SlotInventory("RESP", "Respiratory Medicine", "doc-101", "Dr. Rivera", today.plusDays(1).toString(), "09:00", 6),
                new SlotInventory("RESP", "Respiratory Medicine", "doc-106", "Dr. Murphy", today.plusDays(1).toString(), "14:30", 4),
                new SlotInventory("GEN", "General Medicine", "doc-102", "Dr. Park", today.plusDays(1).toString(), "10:30", 8),
                new SlotInventory("DERM", "Dermatology", "doc-103", "Dr. Patel", today.plusDays(1).toString(), "14:00", 4),
                new SlotInventory("GI", "Gastroenterology", "doc-104", "Dr. Khan", today.plusDays(2).toString(), "09:30", 3),
                new SlotInventory("PED", "Pediatrics", "doc-105", "Dr. Gomez", today.plusDays(2).toString(), "15:00", 5),
                new SlotInventory("GYN", "Gynecology", "doc-107", "Dr. Lopez", today.plusDays(2).toString(), "13:30", 4)
        );
    }

    private record SlotKey(String departmentCode, String doctorId, String clinicDate, String startTime) {
    }

    private static final class SlotInventory {
        private final String departmentCode;
        private final String departmentName;
        private final String doctorId;
        private final String doctorName;
        private final String clinicDate;
        private final String startTime;
        private final int capacity;
        private int remainingSlots;

        private SlotInventory(String departmentCode,
                              String departmentName,
                              String doctorId,
                              String doctorName,
                              String clinicDate,
                              String startTime,
                              int capacity) {
            this.departmentCode = departmentCode;
            this.departmentName = departmentName;
            this.doctorId = doctorId;
            this.doctorName = doctorName;
            this.clinicDate = clinicDate;
            this.startTime = startTime;
            this.capacity = capacity;
            this.remainingSlots = capacity;
        }

        private SlotKey key() {
            return new SlotKey(departmentCode, doctorId, clinicDate, startTime);
        }

        private SlotSummary toSummary() {
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

        private boolean matchesKeyword(String keyword) {
            return departmentCode.toLowerCase().contains(keyword)
                    || departmentName.toLowerCase().contains(keyword)
                    || doctorId.toLowerCase().contains(keyword)
                    || doctorName.toLowerCase().contains(keyword)
                    || clinicDate.toLowerCase().contains(keyword)
                    || startTime.toLowerCase().contains(keyword);
        }
    }
}
