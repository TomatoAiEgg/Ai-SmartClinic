package com.example.airegistration.registration;

import com.example.airegistration.domain.AgentRoute;
import com.example.airegistration.domain.ApiError;
import com.example.airegistration.domain.ChatRequest;
import com.example.airegistration.domain.ChatResponse;
import com.example.airegistration.domain.PatientSummary;
import com.example.airegistration.domain.RegistrationCancelRequest;
import com.example.airegistration.domain.RegistrationCommand;
import com.example.airegistration.domain.RegistrationQueryRequest;
import com.example.airegistration.domain.RegistrationRescheduleRequest;
import com.example.airegistration.domain.RegistrationResult;
import com.example.airegistration.domain.ScheduleSlotRequest;
import com.example.airegistration.domain.SlotSummary;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class RegistrationAgentService {

    private static final Pattern REGISTRATION_ID_PATTERN = Pattern.compile("REG-[A-Z0-9]{8,}", Pattern.CASE_INSENSITIVE);

    private final WebClient patientClient;
    private final WebClient scheduleClient;
    private final WebClient registrationClient;

    public RegistrationAgentService(WebClient.Builder webClientBuilder,
                                    @Value("${app.mcp.patient.base-url}") String patientBaseUrl,
                                    @Value("${app.mcp.schedule.base-url}") String scheduleBaseUrl,
                                    @Value("${app.mcp.registration.base-url}") String registrationBaseUrl) {
        this.patientClient = webClientBuilder.baseUrl(patientBaseUrl).build();
        this.scheduleClient = webClientBuilder.baseUrl(scheduleBaseUrl).build();
        this.registrationClient = webClientBuilder.baseUrl(registrationBaseUrl).build();
    }

    public Mono<ChatResponse> handle(ChatRequest request) {
        RegistrationIntent intent = determineIntent(request);
        return switch (intent) {
            case QUERY -> queryRegistration(request);
            case CANCEL -> cancelRegistration(request);
            case RESCHEDULE -> rescheduleRegistration(request);
            case CREATE -> createRegistration(request);
        };
    }

    private Mono<ChatResponse> createRegistration(ChatRequest request) {
        Mono<PatientSummary> patientMono = fetchDefaultPatient(request.userId());
        Mono<SlotSummary> slotMono = previewCreateSlot(request);

        return Mono.zip(patientMono, slotMono)
                .flatMap(tuple -> buildCreateResponse(request, tuple.getT1(), tuple.getT2()))
                .onErrorResume(ex -> toErrorResponse(
                        request.chatId(),
                        ex,
                        "挂号预处理失败，请检查患者信息和号源服务是否可用。",
                        "create"
                ));
    }

    private Mono<ChatResponse> buildCreateResponse(ChatRequest request, PatientSummary patient, SlotSummary slot) {
        if (!isConfirmed(request)) {
            return Mono.just(buildCreatePreviewResponse(request.chatId(), patient, slot));
        }

        ScheduleSlotRequest slotRequest = toSlotRequest(slot);
        RegistrationCommand command = new RegistrationCommand(
                request.userId(),
                patient.patientId(),
                slot.departmentCode(),
                slot.doctorId(),
                slot.clinicDate(),
                slot.startTime(),
                true
        );

        return reserveSlot(slotRequest)
                .flatMap(ignored -> postRegistration("/api/mcp/registrations", command)
                        .map(result -> toResultResponse(request.chatId(), result))
                        .onErrorResume(ex -> rollbackReservedSlot(slotRequest, ex)))
                .onErrorResume(ex -> toErrorResponse(
                        request.chatId(),
                        ex,
                        "挂号操作失败，请稍后重试。",
                        "create"
                ));
    }

    private ChatResponse buildCreatePreviewResponse(String chatId, PatientSummary patient, SlotSummary slot) {
        Map<String, Object> data = new HashMap<>();
        data.put("action", "create");
        data.put("patientId", patient.patientId());
        data.put("patientName", patient.name());
        data.put("departmentCode", slot.departmentCode());
        data.put("departmentName", slot.departmentName());
        data.put("doctorId", slot.doctorId());
        data.put("doctorName", slot.doctorName());
        data.put("clinicDate", slot.clinicDate());
        data.put("startTime", slot.startTime());
        data.put("remainingSlots", slot.remainingSlots());
        return new ChatResponse(
                chatId,
                AgentRoute.REGISTRATION,
                "已生成挂号预览：%s，医生 %s，时间 %s %s。请确认后再提交挂号。"
                        .formatted(slot.departmentName(), slot.doctorName(), slot.clinicDate(), slot.startTime()),
                true,
                Map.copyOf(data)
        );
    }

    private Mono<ChatResponse> queryRegistration(ChatRequest request) {
        String registrationId = extractRegistrationId(request);
        if (registrationId == null) {
            return Mono.just(new ChatResponse(
                    request.chatId(),
                    AgentRoute.REGISTRATION,
                    "查询挂号需要提供挂号单号，例如 REG-1234ABCD。",
                    false,
                    Map.of("action", "query")
            ));
        }

        return queryRegistrationResult(registrationId)
                .map(result -> toResultResponse(request.chatId(), result))
                .onErrorResume(ex -> toErrorResponse(
                        request.chatId(),
                        ex,
                        "挂号查询失败，请稍后重试。",
                        "query"
                ));
    }

    private Mono<ChatResponse> cancelRegistration(ChatRequest request) {
        String registrationId = extractRegistrationId(request);
        if (registrationId == null) {
            return Mono.just(new ChatResponse(
                    request.chatId(),
                    AgentRoute.REGISTRATION,
                    "取消挂号需要提供挂号单号，例如 REG-1234ABCD。",
                    false,
                    Map.of("action", "cancel")
            ));
        }

        return queryRegistrationResult(registrationId)
                .flatMap(existing -> {
                    if ("CANCELLED".equals(existing.status())) {
                        return Mono.just(new ChatResponse(
                                request.chatId(),
                                AgentRoute.REGISTRATION,
                                "该挂号单已经取消，无需重复处理。",
                                false,
                                Map.of(
                                        "action", "cancel",
                                        "registrationId", existing.registrationId(),
                                        "status", existing.status()
                                )
                        ));
                    }
                    if (!isConfirmed(request)) {
                        return Mono.just(buildCancelPreviewResponse(request.chatId(), existing));
                    }

                    RegistrationCancelRequest cancelRequest = new RegistrationCancelRequest(
                            existing.registrationId(),
                            request.userId(),
                            true,
                            request.metadata().getOrDefault("reason", "user_requested")
                    );

                    return postRegistration("/api/mcp/registrations/cancel", cancelRequest)
                            .flatMap(result -> releaseSlot(toSlotRequest(existing))
                                    .map(ignored -> toResultResponse(request.chatId(), result))
                                    .onErrorResume(ex -> Mono.just(withWarning(
                                            request.chatId(),
                                            result,
                                            ex,
                                            "号源释放失败，请人工核对。"
                                    ))));
                })
                .onErrorResume(ex -> toErrorResponse(
                        request.chatId(),
                        ex,
                        "取消挂号失败，请稍后重试。",
                        "cancel"
                ));
    }

    private ChatResponse buildCancelPreviewResponse(String chatId, RegistrationResult existing) {
        return new ChatResponse(
                chatId,
                AgentRoute.REGISTRATION,
                "将取消挂号单 %s，时间 %s %s。请确认后继续。"
                        .formatted(existing.registrationId(), existing.clinicDate(), existing.startTime()),
                true,
                Map.of(
                        "action", "cancel",
                        "registrationId", existing.registrationId(),
                        "status", existing.status(),
                        "departmentCode", existing.departmentCode(),
                        "doctorId", existing.doctorId(),
                        "clinicDate", existing.clinicDate(),
                        "startTime", existing.startTime()
                )
        );
    }

    private Mono<ChatResponse> rescheduleRegistration(ChatRequest request) {
        String registrationId = extractRegistrationId(request);
        if (registrationId == null) {
            return Mono.just(new ChatResponse(
                    request.chatId(),
                    AgentRoute.REGISTRATION,
                    "改约需要提供挂号单号，例如 REG-1234ABCD。",
                    false,
                    Map.of("action", "reschedule")
            ));
        }

        String clinicDate = normalizeText(request.metadata().get("clinicDate"));
        String startTime = normalizeText(request.metadata().get("startTime"));
        if (isBlank(clinicDate) || isBlank(startTime)) {
            return Mono.just(new ChatResponse(
                    request.chatId(),
                    AgentRoute.REGISTRATION,
                    "改约需要提供新的就诊日期和开始时间，请在 metadata 中传入 clinicDate 和 startTime。",
                    false,
                    Map.of("action", "reschedule", "registrationId", registrationId)
            ));
        }

        return queryRegistrationResult(registrationId)
                .flatMap(existing -> {
                    if ("CANCELLED".equals(existing.status())) {
                        return Mono.just(new ChatResponse(
                                request.chatId(),
                                AgentRoute.REGISTRATION,
                                "已取消的挂号单不能改约。",
                                false,
                                Map.of(
                                        "action", "reschedule",
                                        "registrationId", existing.registrationId(),
                                        "status", existing.status()
                                )
                        ));
                    }

                    ScheduleSlotRequest targetSlotRequest = buildRescheduleSlotRequest(request, existing, clinicDate, startTime);
                    if (sameSlot(existing, targetSlotRequest)) {
                        return Mono.just(new ChatResponse(
                                request.chatId(),
                                AgentRoute.REGISTRATION,
                                "新的时间与当前挂号一致，无需改约。",
                                false,
                                Map.of(
                                        "action", "reschedule",
                                        "registrationId", existing.registrationId(),
                                        "clinicDate", clinicDate,
                                        "startTime", startTime
                                )
                        ));
                    }

                    return resolveSlot(targetSlotRequest)
                            .flatMap(targetSlot -> {
                                if (!isConfirmed(request)) {
                                    return Mono.just(buildReschedulePreviewResponse(request.chatId(), existing, targetSlot));
                                }

                                RegistrationRescheduleRequest rescheduleRequest = new RegistrationRescheduleRequest(
                                        existing.registrationId(),
                                        request.userId(),
                                        targetSlot.clinicDate(),
                                        targetSlot.startTime(),
                                        true
                                );

                                return reserveSlot(targetSlotRequest)
                                        .flatMap(ignored -> postRegistration("/api/mcp/registrations/reschedule", rescheduleRequest)
                                                .flatMap(result -> releaseSlot(toSlotRequest(existing))
                                                        .map(released -> toResultResponse(request.chatId(), result))
                                                        .onErrorResume(ex -> Mono.just(withWarning(
                                                                request.chatId(),
                                                                result,
                                                                ex,
                                                                "原号源释放失败，请人工核对。"
                                                        ))))
                                                .onErrorResume(ex -> rollbackReservedSlot(targetSlotRequest, ex)));
                            });
                })
                .onErrorResume(ex -> toErrorResponse(
                        request.chatId(),
                        ex,
                        "改约操作失败，请稍后重试。",
                        "reschedule"
                ));
    }

    private ChatResponse buildReschedulePreviewResponse(String chatId, RegistrationResult existing, SlotSummary targetSlot) {
        return new ChatResponse(
                chatId,
                AgentRoute.REGISTRATION,
                "已生成改约预览：挂号单 %s 将调整到 %s %s。请确认后继续。"
                        .formatted(existing.registrationId(), targetSlot.clinicDate(), targetSlot.startTime()),
                true,
                Map.of(
                        "action", "reschedule",
                        "registrationId", existing.registrationId(),
                        "departmentCode", targetSlot.departmentCode(),
                        "departmentName", targetSlot.departmentName(),
                        "doctorId", targetSlot.doctorId(),
                        "doctorName", targetSlot.doctorName(),
                        "clinicDate", targetSlot.clinicDate(),
                        "startTime", targetSlot.startTime(),
                        "remainingSlots", targetSlot.remainingSlots()
                )
        );
    }

    private ScheduleSlotRequest buildRescheduleSlotRequest(ChatRequest request,
                                                           RegistrationResult existing,
                                                           String clinicDate,
                                                           String startTime) {
        String departmentCode = normalizeText(request.metadata().get("departmentCode"));
        String doctorId = normalizeText(request.metadata().get("doctorId"));
        if (!isBlank(departmentCode) && !existing.departmentCode().equalsIgnoreCase(departmentCode)) {
            throw new RegistrationAgentException(new ApiError(
                    "INVALID_REQUEST",
                    "当前版本只支持同科室改约。",
                    Map.of("registrationId", existing.registrationId())
            ), "registration-agent");
        }
        if (!isBlank(doctorId) && !existing.doctorId().equalsIgnoreCase(doctorId)) {
            throw new RegistrationAgentException(new ApiError(
                    "INVALID_REQUEST",
                    "当前版本只支持同医生改约。",
                    Map.of("registrationId", existing.registrationId())
            ), "registration-agent");
        }
        return new ScheduleSlotRequest(existing.departmentCode(), existing.doctorId(), clinicDate, startTime);
    }

    private Mono<SlotSummary> previewCreateSlot(ChatRequest request) {
        ScheduleSlotRequest exactSlot = extractExactSlotRequest(request);
        if (exactSlot != null) {
            return resolveSlot(exactSlot);
        }
        return recommendSlot(resolveDepartmentCode(request));
    }

    private ScheduleSlotRequest extractExactSlotRequest(ChatRequest request) {
        String departmentCode = normalizeText(request.metadata().get("departmentCode"));
        String doctorId = normalizeText(request.metadata().get("doctorId"));
        String clinicDate = normalizeText(request.metadata().get("clinicDate"));
        String startTime = normalizeText(request.metadata().get("startTime"));
        if (isBlank(departmentCode) || isBlank(doctorId) || isBlank(clinicDate) || isBlank(startTime)) {
            return null;
        }
        return new ScheduleSlotRequest(departmentCode.toUpperCase(Locale.ROOT), doctorId, clinicDate, startTime);
    }

    private String resolveDepartmentCode(ChatRequest request) {
        String metadataDepartmentCode = normalizeText(request.metadata().get("departmentCode"));
        if (!isBlank(metadataDepartmentCode)) {
            return metadataDepartmentCode.toUpperCase(Locale.ROOT);
        }

        String text = request.message() == null ? "" : request.message().toLowerCase(Locale.ROOT);
        if (containsAny(text, "resp", "respiratory", "cough", "fever", "呼吸", "咳嗽", "发热", "呼吸内科")) {
            return "RESP";
        }
        if (containsAny(text, "derm", "dermatology", "skin", "rash", "皮肤", "皮肤科")) {
            return "DERM";
        }
        if (containsAny(text, "gi", "gastro", "stomach", "abdomen", "digestive", "消化", "胃", "肚子", "消化内科")) {
            return "GI";
        }
        if (containsAny(text, "ped", "pediatrics", "child", "baby", "儿科", "儿童", "小孩")) {
            return "PED";
        }
        if (containsAny(text, "gyn", "gynecology", "pregnant", "menstrual", "妇科", "怀孕")) {
            return "GYN";
        }
        return "GEN";
    }

    private Mono<PatientSummary> fetchDefaultPatient(String userId) {
        return patientClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/mcp/patients/default")
                        .queryParam("userId", userId)
                        .build())
                .exchangeToMono(response -> decodeResponse(response, PatientSummary.class, "patient-mcp-server"));
    }

    private Mono<SlotSummary> recommendSlot(String departmentCode) {
        return scheduleClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/mcp/schedules/recommend")
                        .queryParam("departmentCode", departmentCode)
                        .build())
                .exchangeToMono(response -> decodeResponse(response, SlotSummary.class, "schedule-mcp-server"));
    }

    private Mono<SlotSummary> resolveSlot(ScheduleSlotRequest request) {
        return postForBody(scheduleClient, "/api/mcp/schedules/resolve", request, SlotSummary.class, "schedule-mcp-server");
    }

    private Mono<SlotSummary> reserveSlot(ScheduleSlotRequest request) {
        return postForBody(scheduleClient, "/api/mcp/schedules/reserve", request, SlotSummary.class, "schedule-mcp-server");
    }

    private Mono<SlotSummary> releaseSlot(ScheduleSlotRequest request) {
        return postForBody(scheduleClient, "/api/mcp/schedules/release", request, SlotSummary.class, "schedule-mcp-server");
    }

    private Mono<RegistrationResult> queryRegistrationResult(String registrationId) {
        return postForBody(
                registrationClient,
                "/api/mcp/registrations/query",
                new RegistrationQueryRequest(registrationId),
                RegistrationResult.class,
                "registration-mcp-server"
        );
    }

    private Mono<RegistrationResult> postRegistration(String path, Object body) {
        return postForBody(registrationClient, path, body, RegistrationResult.class, "registration-mcp-server");
    }

    private <T> Mono<T> postForBody(WebClient client, String path, Object body, Class<T> bodyType, String source) {
        return client.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(response -> decodeResponse(response, bodyType, source));
    }

    private <T> Mono<T> decodeResponse(ClientResponse response, Class<T> bodyType, String source) {
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(bodyType);
        }
        return response.bodyToMono(ApiError.class)
                .defaultIfEmpty(new ApiError("REMOTE_ERROR", source + " returned an error.", Map.of()))
                .flatMap(error -> Mono.error(new RegistrationAgentException(error, source)));
    }

    private <T> Mono<T> rollbackReservedSlot(ScheduleSlotRequest slotRequest, Throwable originalError) {
        return releaseSlot(slotRequest)
                .onErrorResume(releaseError -> Mono.empty())
                .then(Mono.error(originalError));
    }

    private ChatResponse toResultResponse(String chatId, RegistrationResult result) {
        return toResultResponse(chatId, result, Map.of(), result.message());
    }

    private ChatResponse withWarning(String chatId, RegistrationResult result, Throwable throwable, String warningSuffix) {
        String warning = extractErrorMessage(throwable);
        Map<String, Object> extraData = new HashMap<>();
        extraData.put("warning", warning);
        String warningCode = extractErrorCode(throwable);
        if (!isBlank(warningCode)) {
            extraData.put("warningCode", warningCode);
        }
        return toResultResponse(chatId, result, extraData, result.message() + warningSuffix);
    }

    private ChatResponse toResultResponse(String chatId,
                                          RegistrationResult result,
                                          Map<String, Object> extraData,
                                          String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("registrationId", result.registrationId());
        data.put("status", result.status());
        data.put("patientId", result.patientId());
        data.put("departmentCode", result.departmentCode());
        data.put("doctorId", result.doctorId());
        data.put("clinicDate", result.clinicDate());
        data.put("startTime", result.startTime());
        data.putAll(extraData);
        return new ChatResponse(chatId, AgentRoute.REGISTRATION, message, false, Map.copyOf(data));
    }

    private Mono<ChatResponse> toErrorResponse(String chatId, Throwable throwable, String fallbackMessage, String action) {
        if (throwable instanceof RegistrationAgentException ex) {
            Map<String, Object> data = new HashMap<>();
            data.put("action", action);
            data.put("source", ex.source());
            data.put("code", ex.error().code());
            if (!ex.error().details().isEmpty()) {
                data.put("details", ex.error().details());
            }
            return Mono.just(new ChatResponse(
                    chatId,
                    AgentRoute.REGISTRATION,
                    ex.error().message(),
                    false,
                    Map.copyOf(data)
            ));
        }
        return Mono.just(new ChatResponse(
                chatId,
                AgentRoute.REGISTRATION,
                fallbackMessage,
                false,
                Map.of(
                        "action", action,
                        "error", throwable.getMessage() == null ? fallbackMessage : throwable.getMessage()
                )
        ));
    }

    private RegistrationIntent determineIntent(ChatRequest request) {
        String action = request.metadata().get("action");
        if (!isBlank(action)) {
            return switch (action.trim().toLowerCase(Locale.ROOT)) {
                case "query" -> RegistrationIntent.QUERY;
                case "cancel" -> RegistrationIntent.CANCEL;
                case "reschedule" -> RegistrationIntent.RESCHEDULE;
                default -> RegistrationIntent.CREATE;
            };
        }

        String text = request.message() == null ? "" : request.message().toLowerCase(Locale.ROOT);
        if (containsAny(text, "cancel", "取消")) {
            return RegistrationIntent.CANCEL;
        }
        if (containsAny(text, "reschedule", "change time", "改约", "改时间")) {
            return RegistrationIntent.RESCHEDULE;
        }
        if (containsAny(text, "query", "status", "查询", "状态", "单号")) {
            return RegistrationIntent.QUERY;
        }
        return RegistrationIntent.CREATE;
    }

    private boolean isConfirmed(ChatRequest request) {
        String message = request.message() == null ? "" : request.message().toLowerCase(Locale.ROOT);
        return "true".equalsIgnoreCase(request.metadata().getOrDefault("confirmed", "false"))
                || containsAny(message, "confirm", "confirmed", "yes", "确认", "是的");
    }

    private String extractRegistrationId(ChatRequest request) {
        String metadataId = request.metadata().get("registrationId");
        if (!isBlank(metadataId)) {
            return metadataId.trim().toUpperCase(Locale.ROOT);
        }

        String text = request.message() == null ? "" : request.message().toUpperCase(Locale.ROOT);
        Matcher matcher = REGISTRATION_ID_PATTERN.matcher(text);
        return matcher.find() ? matcher.group().toUpperCase(Locale.ROOT) : null;
    }

    private ScheduleSlotRequest toSlotRequest(SlotSummary slot) {
        return new ScheduleSlotRequest(slot.departmentCode(), slot.doctorId(), slot.clinicDate(), slot.startTime());
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

    private String extractErrorMessage(Throwable throwable) {
        if (throwable instanceof RegistrationAgentException ex) {
            return ex.error().message();
        }
        return throwable.getMessage() == null ? "Unknown error." : throwable.getMessage();
    }

    private String extractErrorCode(Throwable throwable) {
        if (throwable instanceof RegistrationAgentException ex) {
            return ex.error().code();
        }
        return "";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private enum RegistrationIntent {
        CREATE,
        QUERY,
        CANCEL,
        RESCHEDULE
    }

    private static final class RegistrationAgentException extends RuntimeException {
        private final ApiError error;
        private final String source;

        private RegistrationAgentException(ApiError error, String source) {
            super(error.message());
            this.error = error;
            this.source = source;
        }

        private ApiError error() {
            return error;
        }

        private String source() {
            return source;
        }
    }
}
