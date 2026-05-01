package com.example.airegistration.registration.service.tool;

import com.example.airegistration.dto.ApiError;
import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.PatientSummary;
import com.example.airegistration.dto.RegistrationCancelRequest;
import com.example.airegistration.dto.RegistrationCommand;
import com.example.airegistration.dto.RegistrationRescheduleRequest;
import com.example.airegistration.dto.RegistrationResult;
import com.example.airegistration.dto.RegistrationSearchRequest;
import com.example.airegistration.dto.RegistrationSearchResponse;
import com.example.airegistration.dto.ScheduleSlotRequest;
import com.example.airegistration.dto.SlotSummary;
import com.example.airegistration.enums.ApiErrorCode;
import com.example.airegistration.registration.client.McpRegistrationGateway;
import com.example.airegistration.registration.enums.RegistrationIntent;
import com.example.airegistration.registration.exception.RegistrationAgentException;
import com.example.airegistration.registration.service.RegistrationConfirmationContext;
import com.example.airegistration.registration.service.RegistrationConfirmationService;
import com.example.airegistration.registration.service.RegistrationFlowPolicy;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class RegistrationToolService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationToolService.class);

    private final RegistrationFlowPolicy flowPolicy;
    private final McpRegistrationGateway mcpGateway;
    private final RegistrationConfirmationService confirmationService;

    public RegistrationToolService(RegistrationFlowPolicy flowPolicy,
                                   McpRegistrationGateway mcpGateway,
                                   RegistrationConfirmationService confirmationService) {
        this.flowPolicy = flowPolicy;
        this.mcpGateway = mcpGateway;
        this.confirmationService = confirmationService;
    }

    public Mono<PatientSummary> fetchDefaultPatient(String traceId, String userId) {
        log.info("[registration->patient-mcp] fetch default patient trace_id={} user_id={}", traceId, userId);
        return mcpGateway.fetchDefaultPatient(traceId, userId);
    }

    public Mono<PatientSummary> fetchDefaultPatient(String userId) {
        return fetchDefaultPatient("", userId);
    }

    public Mono<SlotSummary> previewCreateSlot(ChatRequest request, String departmentCode, String scheduleSearchKeyword) {
        log.info("[registration->schedule-mcp] preview create slot trace_id={} chat_id={} department_code={} search_keyword={}",
                request.traceId(),
                request.chatId(),
                departmentCode,
                scheduleSearchKeyword);
        ScheduleSlotRequest exactSlot = flowPolicy.extractExactSlotRequest(request);
        if (exactSlot != null) {
            return mcpGateway.resolveSlot(request.traceId(), exactSlot);
        }
        String doctorId = flowPolicy.resolveDoctorId(request);
        String clinicDate = flowPolicy.resolveClinicDate(request);
        String startTime = flowPolicy.resolveStartTime(request);
        String searchKeyword = flowPolicy.isBlank(scheduleSearchKeyword) ? departmentCode : scheduleSearchKeyword;
        boolean needsSearch = !flowPolicy.isBlank(searchKeyword)
                && (flowPolicy.isBlank(departmentCode)
                || !flowPolicy.isBlank(doctorId)
                || !flowPolicy.isBlank(clinicDate)
                || !flowPolicy.isBlank(startTime));
        if (needsSearch) {
            return mcpGateway.searchSlots(request.traceId(), searchKeyword)
                    .map(response -> selectCreatePreviewSlot(
                            response.slots(),
                            departmentCode,
                            doctorId,
                            clinicDate,
                            startTime,
                            searchKeyword
                    ));
        }
        return mcpGateway.recommendSlot(request.traceId(), departmentCode);
    }

    public ScheduleSlotRequest buildRescheduleSlotRequest(ChatRequest request,
                                                          RegistrationResult existing,
                                                          String clinicDate,
                                                          String startTime) {
        String departmentCode = flowPolicy.resolveDepartmentCode(request);
        String doctorId = flowPolicy.resolveDoctorId(request);
        return new ScheduleSlotRequest(
                flowPolicy.isBlank(departmentCode) ? existing.departmentCode() : departmentCode.toUpperCase(Locale.ROOT),
                flowPolicy.isBlank(doctorId) ? existing.doctorId() : doctorId,
                clinicDate,
                startTime
        );
    }

    public Mono<String> saveConfirmation(ChatRequest request, RegistrationIntent intent, Map<String, Object> data) {
        log.info("[registration] save confirmation trace_id={} chat_id={} intent={} data_keys={}",
                request.traceId(),
                request.chatId(),
                intent,
                data.keySet());
        return confirmationService.save(request, intent, data);
    }

    public Mono<RegistrationConfirmationContext> consumeConfirmation(ChatRequest request, RegistrationIntent intent) {
        log.info("[registration] consume confirmation trace_id={} chat_id={} intent={} metadata_keys={}",
                request.traceId(),
                request.chatId(),
                intent,
                request.metadata().keySet());
        return confirmationService.consume(request, intent);
    }

    public Mono<RegistrationSearchResponse> searchRegistrations(String traceId, RegistrationSearchRequest request) {
        log.info("[registration->registration-mcp] search registrations trace_id={} user_id={} clinic_date={} department_code={} doctor_id={} status={}",
                traceId,
                request.userId(),
                request.clinicDate(),
                request.departmentCode(),
                request.doctorId(),
                request.status());
        return mcpGateway.searchRegistrations(traceId, request);
    }

    public Mono<RegistrationSearchResponse> searchRegistrations(RegistrationSearchRequest request) {
        return searchRegistrations("", request);
    }

    public Mono<RegistrationResult> queryRegistrationResult(String traceId, String registrationId, String userId) {
        log.info("[registration->registration-mcp] query registration trace_id={} registration_id={} user_id={}",
                traceId,
                registrationId,
                userId);
        return mcpGateway.queryRegistrationResult(traceId, registrationId, userId);
    }

    public Mono<RegistrationResult> queryRegistrationResult(String registrationId, String userId) {
        return queryRegistrationResult("", registrationId, userId);
    }

    public Mono<RegistrationResult> createRegistration(String traceId, RegistrationCommand command) {
        log.info("[registration->registration-mcp] create registration trace_id={} user_id={} patient_id={} department_code={} doctor_id={} clinic_date={} start_time={}",
                traceId,
                command.userId(),
                command.patientId(),
                command.departmentCode(),
                command.doctorId(),
                command.clinicDate(),
                command.startTime());
        return mcpGateway.createRegistration(traceId, command);
    }

    public Mono<RegistrationResult> createRegistration(RegistrationCommand command) {
        return createRegistration("", command);
    }

    public Mono<RegistrationResult> cancelRegistration(String traceId, RegistrationCancelRequest request) {
        log.info("[registration->registration-mcp] cancel registration trace_id={} registration_id={} user_id={} confirmed={}",
                traceId,
                request.registrationId(),
                request.userId(),
                request.confirmed());
        return mcpGateway.cancelRegistration(traceId, request);
    }

    public Mono<RegistrationResult> cancelRegistration(RegistrationCancelRequest request) {
        return cancelRegistration("", request);
    }

    public Mono<RegistrationResult> rescheduleRegistration(String traceId, RegistrationRescheduleRequest request) {
        log.info("[registration->registration-mcp] reschedule registration trace_id={} registration_id={} user_id={} clinic_date={} start_time={} confirmed={}",
                traceId,
                request.registrationId(),
                request.userId(),
                request.clinicDate(),
                request.startTime(),
                request.confirmed());
        return mcpGateway.rescheduleRegistration(traceId, request);
    }

    public Mono<RegistrationResult> rescheduleRegistration(RegistrationRescheduleRequest request) {
        return rescheduleRegistration("", request);
    }

    public Mono<SlotSummary> resolveSlot(String traceId, ScheduleSlotRequest request) {
        log.info("[registration->schedule-mcp] resolve slot trace_id={} department_code={} doctor_id={} clinic_date={} start_time={}",
                traceId,
                request.departmentCode(),
                request.doctorId(),
                request.clinicDate(),
                request.startTime());
        return mcpGateway.resolveSlot(traceId, request);
    }

    public Mono<SlotSummary> resolveSlot(ScheduleSlotRequest request) {
        return resolveSlot("", request);
    }

    public Mono<SlotSummary> reserveSlot(String traceId, ScheduleSlotRequest request) {
        log.info("[registration->schedule-mcp] reserve slot trace_id={} department_code={} doctor_id={} clinic_date={} start_time={}",
                traceId,
                request.departmentCode(),
                request.doctorId(),
                request.clinicDate(),
                request.startTime());
        return mcpGateway.reserveSlot(traceId, request);
    }

    public Mono<SlotSummary> reserveSlot(ScheduleSlotRequest request) {
        return reserveSlot("", request);
    }

    public Mono<SlotSummary> releaseSlot(String traceId, ScheduleSlotRequest request) {
        log.info("[registration->schedule-mcp] release slot trace_id={} department_code={} doctor_id={} clinic_date={} start_time={}",
                traceId,
                request.departmentCode(),
                request.doctorId(),
                request.clinicDate(),
                request.startTime());
        return mcpGateway.releaseSlot(traceId, request);
    }

    public Mono<SlotSummary> releaseSlot(ScheduleSlotRequest request) {
        return releaseSlot("", request);
    }

    public <T> Mono<T> rollbackReservedSlot(String traceId, ScheduleSlotRequest slotRequest, Throwable originalError) {
        log.warn("[registration->schedule-mcp] rollback reserved slot trace_id={} department_code={} doctor_id={} clinic_date={} start_time={} reason={}",
                traceId,
                slotRequest.departmentCode(),
                slotRequest.doctorId(),
                slotRequest.clinicDate(),
                slotRequest.startTime(),
                originalError.getMessage());
        return releaseSlot(traceId, slotRequest)
                .onErrorResume(releaseError -> Mono.empty())
                .then(Mono.error(originalError));
    }

    public <T> Mono<T> rollbackReservedSlot(ScheduleSlotRequest slotRequest, Throwable originalError) {
        return rollbackReservedSlot("", slotRequest, originalError);
    }

    private SlotSummary selectCreatePreviewSlot(List<SlotSummary> slots,
                                                String departmentCode,
                                                String doctorId,
                                                String clinicDate,
                                                String startTime,
                                                String searchKeyword) {
        return slots.stream()
                .filter(slot -> slot.remainingSlots() > 0)
                .filter(slot -> flowPolicy.isBlank(departmentCode)
                        || slot.departmentCode().equalsIgnoreCase(departmentCode))
                .filter(slot -> flowPolicy.isBlank(doctorId)
                        || slot.doctorId().equalsIgnoreCase(doctorId))
                .filter(slot -> flowPolicy.isBlank(clinicDate)
                        || slot.clinicDate().equals(clinicDate))
                .filter(slot -> flowPolicy.isBlank(startTime)
                        || slot.startTime().equals(startTime))
                .findFirst()
                .orElseThrow(() -> new RegistrationAgentException(new ApiError(
                        ApiErrorCode.NOT_FOUND,
                        "没有找到匹配的可用号源。",
                        Map.of(
                                "keyword", searchKeyword,
                                "departmentCode", normalize(departmentCode),
                                "doctorId", normalize(doctorId),
                                "clinicDate", normalize(clinicDate),
                                "startTime", normalize(startTime)
                        )
                ), "schedule-mcp-server"));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
