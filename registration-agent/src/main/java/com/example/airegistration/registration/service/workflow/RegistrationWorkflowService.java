package com.example.airegistration.registration.service.workflow;

import com.example.airegistration.dto.ApiError;
import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.dto.PatientSummary;
import com.example.airegistration.dto.RegistrationCancelRequest;
import com.example.airegistration.dto.RegistrationCommand;
import com.example.airegistration.dto.RegistrationQueryRequest;
import com.example.airegistration.dto.RegistrationRescheduleRequest;
import com.example.airegistration.dto.RegistrationResult;
import com.example.airegistration.dto.RegistrationSearchRequest;
import com.example.airegistration.dto.RegistrationSearchResponse;
import com.example.airegistration.dto.ScheduleSlotRequest;
import com.example.airegistration.dto.SlotSummary;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.enums.ApiErrorCode;
import com.example.airegistration.enums.RegistrationStatus;
import com.example.airegistration.registration.enums.RegistrationIntent;
import com.example.airegistration.registration.enums.RegistrationReplyScene;
import com.example.airegistration.registration.exception.RegistrationAgentException;
import com.example.airegistration.registration.service.RegistrationConfirmationContext;
import com.example.airegistration.registration.service.RegistrationFlowPolicy;
import com.example.airegistration.registration.service.RegistrationReplyService;
import com.example.airegistration.registration.service.rag.RegistrationRuleService;
import com.example.airegistration.registration.service.rag.RegistrationWorkflowRules;
import com.example.airegistration.registration.service.tool.RegistrationToolService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class RegistrationWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationWorkflowService.class);
    private static final String PATIENT_MCP_SOURCE = "patient-mcp-server";
    private static final String PATIENT_BINDING_REQUIRED_MESSAGE =
            "当前账号还没有默认就诊人，暂时不能提交挂号。请先完成就诊人绑定后再试。";

    private final RegistrationFlowPolicy flowPolicy;
    private final RegistrationReplyService replyService;
    private final RegistrationRuleService registrationRuleService;
    private final RegistrationToolService registrationToolService;

    public RegistrationWorkflowService(RegistrationFlowPolicy flowPolicy,
                                       RegistrationReplyService replyService,
                                       RegistrationRuleService registrationRuleService,
                                       RegistrationToolService registrationToolService) {
        this.flowPolicy = flowPolicy;
        this.replyService = replyService;
        this.registrationRuleService = registrationRuleService;
        this.registrationToolService = registrationToolService;
    }

    public Mono<ChatResponse> handle(ChatRequest request, RegistrationIntent intent) {
        log.info("[registration] workflow entered trace_id={} chat_id={} intent={} confirmation={}",
                request.traceId(),
                request.chatId(),
                intent,
                isConfirming(request));
        return switch (intent) {
            case QUERY -> queryRegistration(request);
            case CANCEL -> cancelRegistration(request);
            case RESCHEDULE -> rescheduleRegistration(request);
            case CREATE -> createRegistration(request);
        };
    }

    private Mono<ChatResponse> createRegistration(ChatRequest request) {
        log.info("[registration] create flow started trace_id={} chat_id={} metadata_keys={}",
                request.traceId(),
                request.chatId(),
                request.metadata().keySet());
        RegistrationWorkflowRules workflowRules =
                registrationRuleService.resolveWorkflowRules(request, RegistrationIntent.CREATE, Map.of("action", "create"));
        if (isConfirming(request)) {
            log.info("[registration] create confirmation requested trace_id={} chat_id={} confirmation_id={}",
                    request.traceId(),
                    request.chatId(),
                    request.metadata().get("confirmationId"));
            return confirmCreateRegistration(request)
                    .onErrorResume(ex -> toErrorResponse(request, ex, "create"));
        }

        List<String> missingFields = resolveMissingFields(request, workflowRules.createRequiredFields());
        String departmentCode = flowPolicy.resolveDepartmentCode(request);
        String scheduleSearchKeyword = flowPolicy.resolveScheduleSearchKeyword(request);
        if (!missingFields.isEmpty() && flowPolicy.isBlank(scheduleSearchKeyword)) {
            log.info("[registration] create missing fields trace_id={} chat_id={} missing_fields={} redirect_to_triage={}",
                    request.traceId(),
                    request.chatId(),
                    missingFields,
                    flowPolicy.shouldRedirectCreateToTriage(request));
            Map<String, Object> replyData = new HashMap<>();
            replyData.put("action", "create");
            replyData.put("requiredField", missingFields.get(0));
            replyData.put("requiredFields", String.join(",", missingFields));
            if (flowPolicy.shouldRedirectCreateToTriage(request)) {
                replyData.put("requiredAction", "triage");
                replyData.put("suggestedRoute", AgentRoute.TRIAGE.name());
                replyData.put("acceptsSymptomDescription", true);
                replyData.put("nextStep", "describeSymptoms");
            }
            return replyService.reply(request, RegistrationReplyScene.CREATE_MISSING_DEPARTMENT, false,
                    Map.copyOf(replyData));
        }

        Mono<PatientSummary> patientMono = registrationToolService.fetchDefaultPatient(request.traceId(), request.userId());
        Mono<SlotSummary> slotMono = registrationToolService.previewCreateSlot(request, departmentCode, scheduleSearchKeyword);

        return Mono.zip(patientMono, slotMono)
                .flatMap(tuple -> buildCreatePreviewResponse(request, tuple.getT1(), tuple.getT2()))
                .onErrorResume(ex -> toErrorResponse(request, ex, "create"));
    }

    private Mono<ChatResponse> confirmCreateRegistration(ChatRequest request) {
        return registrationToolService.consumeConfirmation(request, RegistrationIntent.CREATE)
                .flatMap(context -> {
                    log.info("[registration] create confirmation consumed trace_id={} chat_id={} confirmation_id={}",
                            request.traceId(),
                            request.chatId(),
                            context.confirmationId());
                    Map<String, Object> data = context.data();
                    ScheduleSlotRequest slotRequest = toSlotRequest(data);
                    RegistrationCommand command = new RegistrationCommand(
                            request.userId(),
                            requireString(data, "patientId"),
                            requireString(data, "departmentCode"),
                            requireString(data, "doctorId"),
                            requireString(data, "clinicDate"),
                            requireString(data, "startTime"),
                            true,
                            context.confirmationId(),
                            request.chatId()
                    );

                    return registrationToolService.reserveSlot(request.traceId(), slotRequest)
                            .flatMap(ignored -> registrationToolService.createRegistration(request.traceId(), command)
                                    .flatMap(result -> toResultResponse(request, result, RegistrationReplyScene.CREATE_RESULT))
                                    .onErrorResume(ex -> registrationToolService.rollbackReservedSlot(request.traceId(), slotRequest, ex)))
                            .onErrorResume(ex -> toErrorResponse(request, ex, "create"));
                });
    }

    private Mono<ChatResponse> queryRegistration(ChatRequest request) {
        log.info("[registration] query flow started trace_id={} chat_id={} registration_id={}",
                request.traceId(),
                request.chatId(),
                flowPolicy.extractRegistrationId(request));
        String registrationId = flowPolicy.extractRegistrationId(request);
        if (flowPolicy.isBlank(registrationId)) {
            return searchRegistrations(request);
        }

        return registrationToolService.queryRegistrationResult(request.traceId(), registrationId, request.userId())
                .flatMap(result -> toResultResponse(request, result, RegistrationReplyScene.QUERY_RESULT))
                .onErrorResume(ex -> toErrorResponse(request, ex, "query"));
    }

    private Mono<ChatResponse> searchRegistrations(ChatRequest request) {
        String clinicDate = flowPolicy.resolveClinicDate(request);
        String departmentCode = flowPolicy.resolveDepartmentCode(request);
        String doctorId = flowPolicy.resolveDoctorId(request);
        String status = flowPolicy.resolveRegistrationStatus(request);
        RegistrationSearchRequest searchRequest = new RegistrationSearchRequest(
                request.userId(),
                clinicDate,
                departmentCode,
                doctorId,
                status
        );

        return registrationToolService.searchRegistrations(request.traceId(), searchRequest)
                .flatMap(response -> toQueryListResponse(request, response, clinicDate, departmentCode, doctorId, status))
                .onErrorResume(ex -> toErrorResponse(request, ex, "query"));
    }

    private Mono<ChatResponse> cancelRegistration(ChatRequest request) {
        log.info("[registration] cancel flow started trace_id={} chat_id={} registration_id={} confirmation={}",
                request.traceId(),
                request.chatId(),
                flowPolicy.extractRegistrationId(request),
                isConfirming(request));
        RegistrationWorkflowRules workflowRules =
                registrationRuleService.resolveWorkflowRules(request, RegistrationIntent.CANCEL, Map.of("action", "cancel"));
        if (isConfirming(request)) {
            return confirmCancelRegistration(request)
                    .onErrorResume(ex -> toErrorResponse(request, ex, "cancel"));
        }

        String registrationId = flowPolicy.extractRegistrationId(request);
        if (workflowRules.requireRegistrationId() && flowPolicy.isBlank(registrationId)) {
            return replyService.reply(request, RegistrationReplyScene.CANCEL_MISSING_ID, false,
                    Map.of("action", "cancel", "requiredField", "registrationId"));
        }

        return registrationToolService.queryRegistrationResult(request.traceId(), registrationId, request.userId())
                .flatMap(existing -> {
                    if (RegistrationStatus.CANCELLED.matches(existing.status())) {
                        return replyService.reply(request, RegistrationReplyScene.CANCEL_ALREADY_CANCELLED, false,
                                Map.of(
                                        "action", "cancel",
                                        "registrationId", existing.registrationId(),
                                        "status", existing.status()
                                ));
                    }
                    if (requiresPreviewBeforeWrite(request, RegistrationIntent.CANCEL, workflowRules)) {
                        return buildCancelPreviewResponse(request, existing);
                    }

                    RegistrationCancelRequest cancelRequest = new RegistrationCancelRequest(
                            existing.registrationId(),
                            request.userId(),
                            true,
                            request.metadata().getOrDefault("reason", "user_requested")
                    );

                    return registrationToolService.cancelRegistration(request.traceId(), cancelRequest)
                            .flatMap(result -> registrationToolService.releaseSlot(request.traceId(), toSlotRequest(existing))
                                    .flatMap(ignored -> toResultResponse(request, result, RegistrationReplyScene.CANCEL_RESULT))
                                    .onErrorResume(ex -> withWarning(
                                            request,
                                            result,
                                            ex,
                                            RegistrationReplyScene.SLOT_RELEASE_FAILED
                                    )));
                })
                .onErrorResume(ex -> toErrorResponse(request, ex, "cancel"));
    }

    private Mono<ChatResponse> confirmCancelRegistration(ChatRequest request) {
        return registrationToolService.consumeConfirmation(request, RegistrationIntent.CANCEL)
                .flatMap(context -> {
                    log.info("[registration] cancel confirmation consumed trace_id={} chat_id={} confirmation_id={}",
                            request.traceId(),
                            request.chatId(),
                            context.confirmationId());
                    String registrationId = requireString(context.data(), "registrationId");

                    return registrationToolService.queryRegistrationResult(request.traceId(), registrationId, request.userId())
                            .flatMap(existing -> {
                                if (RegistrationStatus.CANCELLED.matches(existing.status())) {
                                    return replyService.reply(request, RegistrationReplyScene.CANCEL_ALREADY_CANCELLED, false,
                                            Map.of(
                                                    "action", "cancel",
                                                    "registrationId", existing.registrationId(),
                                                    "status", existing.status()
                                            ));
                                }

                                RegistrationCancelRequest cancelRequest = new RegistrationCancelRequest(
                                        existing.registrationId(),
                                        request.userId(),
                                        true,
                                        request.metadata().getOrDefault("reason", "user_requested")
                                );

                                return registrationToolService.cancelRegistration(request.traceId(), cancelRequest)
                                        .flatMap(result -> registrationToolService.releaseSlot(request.traceId(), toSlotRequest(existing))
                                                .flatMap(ignored -> toResultResponse(request, result, RegistrationReplyScene.CANCEL_RESULT))
                                                .onErrorResume(ex -> withWarning(
                                                        request,
                                                        result,
                                                        ex,
                                                        RegistrationReplyScene.SLOT_RELEASE_FAILED
                                                )));
                            });
                });
    }

    private Mono<ChatResponse> rescheduleRegistration(ChatRequest request) {
        log.info("[registration] reschedule flow started trace_id={} chat_id={} registration_id={} confirmation={}",
                request.traceId(),
                request.chatId(),
                flowPolicy.extractRegistrationId(request),
                isConfirming(request));
        RegistrationWorkflowRules workflowRules = registrationRuleService.resolveWorkflowRules(
                request,
                RegistrationIntent.RESCHEDULE,
                Map.of("action", "reschedule")
        );
        if (isConfirming(request)) {
            return confirmRescheduleRegistration(request)
                    .onErrorResume(ex -> toErrorResponse(request, ex, "reschedule"));
        }

        String registrationId = flowPolicy.extractRegistrationId(request);
        if (workflowRules.requireRegistrationId() && flowPolicy.isBlank(registrationId)) {
            return replyService.reply(request, RegistrationReplyScene.RESCHEDULE_MISSING_ID, false,
                    Map.of("action", "reschedule", "requiredField", "registrationId"));
        }

        String clinicDate = flowPolicy.resolveClinicDate(request);
        String startTime = flowPolicy.resolveStartTime(request);
        List<String> missingFields = resolveMissingFields(request, workflowRules.rescheduleRequiredFields());
        if (!missingFields.isEmpty()) {
            return replyService.reply(request, RegistrationReplyScene.RESCHEDULE_MISSING_TARGET_TIME, false,
                    Map.of(
                            "action", "reschedule",
                            "registrationId", registrationId,
                            "requiredFields", String.join(",", missingFields)
                    ));
        }

        return registrationToolService.queryRegistrationResult(request.traceId(), registrationId, request.userId())
                .flatMap(existing -> {
                    if (RegistrationStatus.CANCELLED.matches(existing.status())) {
                        return replyService.reply(request, RegistrationReplyScene.RESCHEDULE_CANCELLED_RECORD, false,
                                Map.of(
                                        "action", "reschedule",
                                        "registrationId", existing.registrationId(),
                                        "status", existing.status()
                                ));
                    }

                    ScheduleSlotRequest targetSlotRequest =
                            registrationToolService.buildRescheduleSlotRequest(request, existing, clinicDate, startTime);
                    validateRescheduleScope(existing, targetSlotRequest, workflowRules);
                    if (sameSlot(existing, targetSlotRequest)) {
                        return replyService.reply(request, RegistrationReplyScene.RESCHEDULE_SAME_SLOT, false,
                                Map.of(
                                        "action", "reschedule",
                                        "registrationId", existing.registrationId(),
                                        "clinicDate", clinicDate,
                                        "startTime", startTime
                                ));
                    }

                    return registrationToolService.resolveSlot(request.traceId(), targetSlotRequest)
                            .flatMap(targetSlot -> {
                                if (requiresPreviewBeforeWrite(request, RegistrationIntent.RESCHEDULE, workflowRules)) {
                                    return buildReschedulePreviewResponse(request, existing, targetSlot);
                                }

                                RegistrationRescheduleRequest rescheduleRequest = new RegistrationRescheduleRequest(
                                        existing.registrationId(),
                                        request.userId(),
                                        targetSlot.clinicDate(),
                                        targetSlot.startTime(),
                                        true
                                );

                                return registrationToolService.reserveSlot(request.traceId(), targetSlotRequest)
                                        .flatMap(ignored -> registrationToolService.rescheduleRegistration(request.traceId(), rescheduleRequest)
                                                .flatMap(result -> registrationToolService.releaseSlot(request.traceId(), toSlotRequest(existing))
                                                        .flatMap(released -> toResultResponse(request, result, RegistrationReplyScene.RESCHEDULE_RESULT))
                                                        .onErrorResume(ex -> withWarning(
                                                                request,
                                                                result,
                                                                ex,
                                                                RegistrationReplyScene.OLD_SLOT_RELEASE_FAILED
                                                        )))
                                                .onErrorResume(ex -> registrationToolService.rollbackReservedSlot(request.traceId(), targetSlotRequest, ex)));
                            });
                })
                .onErrorResume(ex -> toErrorResponse(request, ex, "reschedule"));
    }

    private Mono<ChatResponse> confirmRescheduleRegistration(ChatRequest request) {
        return registrationToolService.consumeConfirmation(request, RegistrationIntent.RESCHEDULE)
                .flatMap(context -> {
                    log.info("[registration] reschedule confirmation consumed trace_id={} chat_id={} confirmation_id={}",
                            request.traceId(),
                            request.chatId(),
                            context.confirmationId());
                    Map<String, Object> data = context.data();
                    String registrationId = requireString(data, "registrationId");
                    String clinicDate = requireString(data, "clinicDate");
                    String startTime = requireString(data, "startTime");

                    return registrationToolService.queryRegistrationResult(request.traceId(), registrationId, request.userId())
                            .flatMap(existing -> {
                                RegistrationWorkflowRules workflowRules = registrationRuleService.resolveWorkflowRules(
                                        request,
                                        RegistrationIntent.RESCHEDULE,
                                        data
                                );
                                if (RegistrationStatus.CANCELLED.matches(existing.status())) {
                                    return replyService.reply(request, RegistrationReplyScene.RESCHEDULE_CANCELLED_RECORD, false,
                                            Map.of(
                                                    "action", "reschedule",
                                                    "registrationId", existing.registrationId(),
                                                    "status", existing.status()
                                            ));
                                }

                                ScheduleSlotRequest targetSlotRequest = toSlotRequest(data);
                                validateRescheduleScope(existing, targetSlotRequest, workflowRules);
                                if (sameSlot(existing, targetSlotRequest)) {
                                    return replyService.reply(request, RegistrationReplyScene.RESCHEDULE_SAME_SLOT, false,
                                            Map.of(
                                                    "action", "reschedule",
                                                    "registrationId", existing.registrationId(),
                                                    "clinicDate", clinicDate,
                                                    "startTime", startTime
                                            ));
                                }

                                RegistrationRescheduleRequest rescheduleRequest = new RegistrationRescheduleRequest(
                                        existing.registrationId(),
                                        request.userId(),
                                        clinicDate,
                                        startTime,
                                        true
                                );

                                return registrationToolService.reserveSlot(request.traceId(), targetSlotRequest)
                                        .flatMap(ignored -> registrationToolService.rescheduleRegistration(request.traceId(), rescheduleRequest)
                                                .flatMap(result -> registrationToolService.releaseSlot(request.traceId(), toSlotRequest(existing))
                                                        .flatMap(released -> toResultResponse(request, result, RegistrationReplyScene.RESCHEDULE_RESULT))
                                                        .onErrorResume(ex -> withWarning(
                                                                request,
                                                                result,
                                                                ex,
                                                                RegistrationReplyScene.OLD_SLOT_RELEASE_FAILED
                                                        )))
                                                .onErrorResume(ex -> registrationToolService.rollbackReservedSlot(request.traceId(), targetSlotRequest, ex)));
                            });
                });
    }

    private Mono<ChatResponse> buildCreatePreviewResponse(ChatRequest request, PatientSummary patient, SlotSummary slot) {
        Map<String, Object> data = new HashMap<>();
        data.put("action", "create");
        data.put("previewed", true);
        data.put("confirmationAction", "create");
        data.put("patientId", patient.patientId());
        data.put("patientName", patient.name());
        data.put("departmentCode", slot.departmentCode());
        data.put("departmentName", slot.departmentName());
        data.put("doctorId", slot.doctorId());
        data.put("doctorName", slot.doctorName());
        data.put("clinicDate", slot.clinicDate());
        data.put("startTime", slot.startTime());
        data.put("remainingSlots", slot.remainingSlots());
        return replyWithConfirmation(request, RegistrationIntent.CREATE, RegistrationReplyScene.CREATE_PREVIEW, data);
    }

    private Mono<ChatResponse> buildCancelPreviewResponse(ChatRequest request, RegistrationResult existing) {
        Map<String, Object> data = new HashMap<>();
        data.put("action", "cancel");
        data.put("previewed", true);
        data.put("confirmationAction", "cancel");
        data.put("registrationId", existing.registrationId());
        data.put("status", existing.status());
        data.put("departmentCode", existing.departmentCode());
        data.put("doctorId", existing.doctorId());
        data.put("clinicDate", existing.clinicDate());
        data.put("startTime", existing.startTime());
        return replyWithConfirmation(request, RegistrationIntent.CANCEL, RegistrationReplyScene.CANCEL_PREVIEW, data);
    }

    private Mono<ChatResponse> buildReschedulePreviewResponse(ChatRequest request,
                                                              RegistrationResult existing,
                                                              SlotSummary targetSlot) {
        Map<String, Object> data = new HashMap<>();
        data.put("action", "reschedule");
        data.put("previewed", true);
        data.put("confirmationAction", "reschedule");
        data.put("registrationId", existing.registrationId());
        data.put("departmentCode", targetSlot.departmentCode());
        data.put("departmentName", targetSlot.departmentName());
        data.put("doctorId", targetSlot.doctorId());
        data.put("doctorName", targetSlot.doctorName());
        data.put("clinicDate", targetSlot.clinicDate());
        data.put("startTime", targetSlot.startTime());
        data.put("remainingSlots", targetSlot.remainingSlots());
        data.put("originalDepartmentCode", existing.departmentCode());
        data.put("originalDoctorId", existing.doctorId());
        data.put("originalClinicDate", existing.clinicDate());
        data.put("originalStartTime", existing.startTime());
        return replyWithConfirmation(request, RegistrationIntent.RESCHEDULE, RegistrationReplyScene.RESCHEDULE_PREVIEW, data);
    }

    private Mono<ChatResponse> toQueryListResponse(ChatRequest request,
                                                   RegistrationSearchResponse response,
                                                   String clinicDate,
                                                   String departmentCode,
                                                   String doctorId,
                                                   String status) {
        List<RegistrationResult> results = response.records().stream()
                .filter(Objects::nonNull)
                .toList();
        List<Map<String, Object>> records = results.stream()
                .map(this::registrationResultData)
                .toList();
        Map<String, Object> data = new HashMap<>();
        data.put("action", "query");
        data.put("queryType", "list");
        data.put("count", records.size());
        data.put("records", records);
        Map<String, Object> filters = new HashMap<>();
        putIfPresent(filters, "clinicDate", clinicDate);
        putIfPresent(filters, "departmentCode", departmentCode);
        putIfPresent(filters, "doctorId", doctorId);
        putIfPresent(filters, "status", status);
        data.put("filters", Map.copyOf(filters));
        return replyService.reply(request, RegistrationReplyScene.QUERY_LIST, false, Map.copyOf(data));
    }

    private Mono<ChatResponse> toResultResponse(ChatRequest request,
                                                RegistrationResult result,
                                                RegistrationReplyScene scene) {
        return toResultResponse(request, result, Map.of(), scene);
    }

    private Mono<ChatResponse> withWarning(ChatRequest request,
                                           RegistrationResult result,
                                           Throwable throwable,
                                           RegistrationReplyScene scene) {
        String warning = extractErrorMessage(throwable);
        Map<String, Object> extraData = new HashMap<>();
        extraData.put("warning", warning);
        Integer warningCode = extractErrorCode(throwable);
        if (warningCode != null) {
            extraData.put("warningCode", warningCode);
        }
        return toResultResponse(request, result, extraData, scene);
    }

    private Mono<ChatResponse> toResultResponse(ChatRequest request,
                                                RegistrationResult result,
                                                Map<String, Object> extraData,
                                                RegistrationReplyScene scene) {
        Map<String, Object> data = registrationResultData(result);
        data.putAll(extraData);
        return replyService.reply(request, scene, false, Map.copyOf(data));
    }

    private Mono<ChatResponse> toErrorResponse(ChatRequest request, Throwable throwable, String action) {
        if (throwable instanceof RegistrationAgentException ex) {
            if (isMissingPatientBinding(ex)) {
                return Mono.just(missingPatientBindingResponse(request, action, ex));
            }

            Map<String, Object> data = new HashMap<>();
            data.put("action", action);
            data.put("source", ex.source());
            data.put("code", ex.error().code());
            if (!ex.error().details().isEmpty()) {
                data.put("details", ex.error().details());
            }
            data.put("errorMessage", ex.error().message());
            return replyService.reply(request, RegistrationReplyScene.ERROR, false, Map.copyOf(data));
        }
        log.warn("[registration] action={} failed with unexpected error trace_id={} chat_id={}",
                action,
                request.traceId(),
                request.chatId(),
                throwable);
        return replyService.reply(request, RegistrationReplyScene.ERROR, false,
                Map.of(
                        "action", action,
                        "errorType", throwable.getClass().getSimpleName(),
                        "error", throwable.getMessage() == null ? "unknown" : throwable.getMessage()
                ));
    }

    private ChatResponse missingPatientBindingResponse(ChatRequest request,
                                                       String action,
                                                       RegistrationAgentException ex) {
        Map<String, Object> data = new HashMap<>();
        data.put("action", action);
        data.put("source", ex.source());
        data.put("code", ex.error().code());
        data.put("requiredAction", "bindPatient");
        data.put("errorMessage", ex.error().message());
        if (!ex.error().details().isEmpty()) {
            data.put("details", ex.error().details());
        }
        return new ChatResponse(
                request.chatId(),
                AgentRoute.REGISTRATION,
                PATIENT_BINDING_REQUIRED_MESSAGE,
                false,
                Map.copyOf(data)
        );
    }

    private Mono<ChatResponse> replyWithConfirmation(ChatRequest request,
                                                     RegistrationIntent intent,
                                                     RegistrationReplyScene scene,
                                                     Map<String, Object> data) {
        Map<String, Object> previewData = new HashMap<>(data);
        return registrationToolService.saveConfirmation(request, intent, Map.copyOf(previewData))
                .flatMap(confirmationId -> {
                    log.info("[registration] confirmation saved trace_id={} chat_id={} intent={} confirmation_id={}",
                            request.traceId(),
                            request.chatId(),
                            intent,
                            confirmationId);
                    previewData.put("confirmationId", confirmationId);
                    return replyService.reply(request, scene, true, Map.copyOf(previewData));
                });
    }

    private Map<String, Object> registrationResultData(RegistrationResult result) {
        Map<String, Object> data = new HashMap<>();
        putIfNonNull(data, "registrationId", result.registrationId());
        putIfNonNull(data, "status", result.status());
        putIfNonNull(data, "patientId", result.patientId());
        putIfNonNull(data, "departmentCode", result.departmentCode());
        putIfNonNull(data, "doctorId", result.doctorId());
        putIfNonNull(data, "clinicDate", result.clinicDate());
        putIfNonNull(data, "startTime", result.startTime());
        return data;
    }

    private ScheduleSlotRequest toSlotRequest(Map<String, Object> data) {
        return new ScheduleSlotRequest(
                requireString(data, "departmentCode"),
                requireString(data, "doctorId"),
                requireString(data, "clinicDate"),
                requireString(data, "startTime")
        );
    }

    private ScheduleSlotRequest toSlotRequest(RegistrationResult result) {
        return new ScheduleSlotRequest(result.departmentCode(), result.doctorId(), result.clinicDate(), result.startTime());
    }

    private boolean sameSlot(RegistrationResult existing, ScheduleSlotRequest targetSlotRequest) {
        return existing.departmentCode().equalsIgnoreCase(targetSlotRequest.departmentCode())
                && existing.doctorId().equalsIgnoreCase(targetSlotRequest.doctorId())
                && existing.clinicDate().equals(targetSlotRequest.clinicDate())
                && existing.startTime().equals(targetSlotRequest.startTime());
    }

    private boolean isMissingPatientBinding(RegistrationAgentException ex) {
        return PATIENT_MCP_SOURCE.equals(ex.source())
                && ex.error().code() == ApiErrorCode.NOT_FOUND.code();
    }

    private String extractErrorMessage(Throwable throwable) {
        if (throwable instanceof RegistrationAgentException ex) {
            return ex.error().message();
        }
        return throwable.getMessage() == null ? "未知错误。" : throwable.getMessage();
    }

    private Integer extractErrorCode(Throwable throwable) {
        if (throwable instanceof RegistrationAgentException ex) {
            return ex.error().code();
        }
        return null;
    }

    private boolean isConfirming(ChatRequest request) {
        return "true".equalsIgnoreCase(flowPolicy.normalizeText(request.metadata().get("confirmed")));
    }

    private boolean requiresPreviewBeforeWrite(ChatRequest request,
                                               RegistrationIntent intent,
                                               RegistrationWorkflowRules workflowRules) {
        return workflowRules.previewBeforeWrite() && !flowPolicy.canExecuteWrite(request, intent);
    }

    private List<String> resolveMissingFields(ChatRequest request, List<String> requiredFields) {
        return requiredFields.stream()
                .filter(field -> flowPolicy.isBlank(resolveFieldValue(request, field)))
                .toList();
    }

    private String resolveFieldValue(ChatRequest request, String field) {
        return switch (field) {
            case "departmentCode" -> flowPolicy.resolveDepartmentCode(request);
            case "doctorId" -> flowPolicy.resolveDoctorId(request);
            case "clinicDate" -> flowPolicy.resolveClinicDate(request);
            case "startTime" -> flowPolicy.resolveStartTime(request);
            case "registrationId" -> flowPolicy.extractRegistrationId(request);
            default -> flowPolicy.normalizeText(request.metadata().get(field));
        };
    }

    private void validateRescheduleScope(RegistrationResult existing,
                                         ScheduleSlotRequest targetSlotRequest,
                                         RegistrationWorkflowRules workflowRules) {
        if (!workflowRules.allowDepartmentChangeOnReschedule()
                && !existing.departmentCode().equalsIgnoreCase(targetSlotRequest.departmentCode())) {
            throw new RegistrationAgentException(new ApiError(
                    ApiErrorCode.INVALID_REQUEST,
                    "当前版本只支持同科室改约。",
                    Map.of("registrationId", existing.registrationId())
            ), "registration-agent");
        }
        if (!workflowRules.allowDoctorChangeOnReschedule()
                && !existing.doctorId().equalsIgnoreCase(targetSlotRequest.doctorId())) {
            throw new RegistrationAgentException(new ApiError(
                    ApiErrorCode.INVALID_REQUEST,
                    "当前版本只支持同医生改约。",
                    Map.of("registrationId", existing.registrationId())
            ), "registration-agent");
        }
    }

    private String requireString(Map<String, Object> data, String field) {
        Object value = data.get(field);
        if (value instanceof String text && !flowPolicy.isBlank(text)) {
            return text.trim();
        }
        throw new RegistrationAgentException(new ApiError(
                ApiErrorCode.INVALID_REQUEST,
                "确认上下文缺少必要字段: " + field,
                Map.of("field", field)
        ), "registration-agent");
    }

    private void putIfPresent(Map<String, Object> data, String key, String value) {
        if (!flowPolicy.isBlank(value)) {
            data.put(key, value);
        }
    }

    private void putIfNonNull(Map<String, Object> data, String key, Object value) {
        if (value != null) {
            data.put(key, value);
        }
    }
}
