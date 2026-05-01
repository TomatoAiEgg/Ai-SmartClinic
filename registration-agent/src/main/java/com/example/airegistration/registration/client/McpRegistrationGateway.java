package com.example.airegistration.registration.client;

import com.example.airegistration.dto.ApiError;
import com.example.airegistration.dto.PatientSummary;
import com.example.airegistration.dto.RegistrationCancelRequest;
import com.example.airegistration.dto.RegistrationCommand;
import com.example.airegistration.dto.RegistrationQueryRequest;
import com.example.airegistration.dto.RegistrationRescheduleRequest;
import com.example.airegistration.dto.RegistrationResult;
import com.example.airegistration.dto.RegistrationSearchRequest;
import com.example.airegistration.dto.RegistrationSearchResponse;
import com.example.airegistration.dto.ScheduleSearchRequest;
import com.example.airegistration.dto.ScheduleSearchResponse;
import com.example.airegistration.dto.ScheduleSlotRequest;
import com.example.airegistration.dto.SlotSummary;
import com.example.airegistration.enums.ApiErrorCode;
import com.example.airegistration.support.TraceIdSupport;
import com.example.airegistration.registration.config.McpClientProperties;
import com.example.airegistration.registration.exception.RegistrationAgentException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@EnableConfigurationProperties(McpClientProperties.class)
public class McpRegistrationGateway {

    private static final Logger log = LoggerFactory.getLogger(McpRegistrationGateway.class);

    private final WebClient patientClient;
    private final WebClient scheduleClient;
    private final WebClient registrationClient;

    public McpRegistrationGateway(WebClient.Builder webClientBuilder, McpClientProperties properties) {
        this.patientClient = webClientBuilder.baseUrl(properties.getPatient().getBaseUrl()).build();
        this.scheduleClient = webClientBuilder.baseUrl(properties.getSchedule().getBaseUrl()).build();
        this.registrationClient = webClientBuilder.baseUrl(properties.getRegistration().getBaseUrl()).build();
    }

    public Mono<PatientSummary> fetchDefaultPatient(String traceId, String userId) {
        return patientClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/mcp/patients/default")
                        .queryParam("userId", userId)
                        .build())
                .header(TraceIdSupport.TRACE_HEADER, traceId)
                .exchangeToMono(response -> decodeResponse(traceId, response, PatientSummary.class, "patient-mcp-server"));
    }

    public Mono<PatientSummary> fetchDefaultPatient(String userId) {
        return fetchDefaultPatient("", userId);
    }

    public Mono<SlotSummary> recommendSlot(String traceId, String departmentCode) {
        return scheduleClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/mcp/schedules/recommend")
                        .queryParam("departmentCode", departmentCode)
                        .build())
                .header(TraceIdSupport.TRACE_HEADER, traceId)
                .exchangeToMono(response -> decodeResponse(traceId, response, SlotSummary.class, "schedule-mcp-server"));
    }

    public Mono<SlotSummary> recommendSlot(String departmentCode) {
        return recommendSlot("", departmentCode);
    }

    public Mono<SlotSummary> resolveSlot(String traceId, ScheduleSlotRequest request) {
        return postForBody(traceId, scheduleClient, "/api/mcp/schedules/resolve", request, SlotSummary.class, "schedule-mcp-server");
    }

    public Mono<SlotSummary> resolveSlot(ScheduleSlotRequest request) {
        return resolveSlot("", request);
    }

    public Mono<ScheduleSearchResponse> searchSlots(String traceId, String keyword) {
        return postForBody(
                traceId,
                scheduleClient,
                "/api/mcp/schedules/search",
                new ScheduleSearchRequest(keyword),
                ScheduleSearchResponse.class,
                "schedule-mcp-server"
        );
    }

    public Mono<ScheduleSearchResponse> searchSlots(String keyword) {
        return searchSlots("", keyword);
    }

    public Mono<SlotSummary> reserveSlot(String traceId, ScheduleSlotRequest request) {
        return postForBody(traceId, scheduleClient, "/api/mcp/schedules/reserve", request, SlotSummary.class, "schedule-mcp-server");
    }

    public Mono<SlotSummary> reserveSlot(ScheduleSlotRequest request) {
        return reserveSlot("", request);
    }

    public Mono<SlotSummary> releaseSlot(String traceId, ScheduleSlotRequest request) {
        return postForBody(traceId, scheduleClient, "/api/mcp/schedules/release", request, SlotSummary.class, "schedule-mcp-server");
    }

    public Mono<SlotSummary> releaseSlot(ScheduleSlotRequest request) {
        return releaseSlot("", request);
    }

    public Mono<RegistrationResult> queryRegistrationResult(String traceId, String registrationId, String userId) {
        return postForBody(
                traceId,
                registrationClient,
                "/api/mcp/registrations/query",
                new RegistrationQueryRequest(registrationId, userId),
                RegistrationResult.class,
                "registration-mcp-server"
        );
    }

    public Mono<RegistrationResult> queryRegistrationResult(String registrationId, String userId) {
        return queryRegistrationResult("", registrationId, userId);
    }

    public Mono<RegistrationSearchResponse> searchRegistrations(String traceId, RegistrationSearchRequest request) {
        return postForBody(
                traceId,
                registrationClient,
                "/api/mcp/registrations/search",
                request,
                RegistrationSearchResponse.class,
                "registration-mcp-server"
        );
    }

    public Mono<RegistrationSearchResponse> searchRegistrations(RegistrationSearchRequest request) {
        return searchRegistrations("", request);
    }

    public Mono<RegistrationResult> createRegistration(String traceId, RegistrationCommand command) {
        return postForBody(traceId, registrationClient, "/api/mcp/registrations", command,
                RegistrationResult.class, "registration-mcp-server");
    }

    public Mono<RegistrationResult> createRegistration(RegistrationCommand command) {
        return createRegistration("", command);
    }

    public Mono<RegistrationResult> cancelRegistration(String traceId, RegistrationCancelRequest request) {
        return postForBody(traceId, registrationClient, "/api/mcp/registrations/cancel", request,
                RegistrationResult.class, "registration-mcp-server");
    }

    public Mono<RegistrationResult> cancelRegistration(RegistrationCancelRequest request) {
        return cancelRegistration("", request);
    }

    public Mono<RegistrationResult> rescheduleRegistration(String traceId, RegistrationRescheduleRequest request) {
        return postForBody(traceId, registrationClient, "/api/mcp/registrations/reschedule", request,
                RegistrationResult.class, "registration-mcp-server");
    }

    public Mono<RegistrationResult> rescheduleRegistration(RegistrationRescheduleRequest request) {
        return rescheduleRegistration("", request);
    }

    private <T> Mono<T> postForBody(String traceId,
                                    WebClient client,
                                    String path,
                                    Object body,
                                    Class<T> bodyType,
                                    String source) {
        log.info("[registration-agent] outbound MCP request trace_id={} source={} path={} body_type={}",
                traceId,
                source,
                path,
                body.getClass().getSimpleName());
        return client.post()
                .uri(path)
                .header(TraceIdSupport.TRACE_HEADER, traceId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(response -> decodeResponse(traceId, response, bodyType, source));
    }

    private <T> Mono<T> decodeResponse(String traceId, ClientResponse response, Class<T> bodyType, String source) {
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(bodyType)
                    .doOnSuccess(ignored -> log.info(
                            "[registration-agent] MCP response trace_id={} source={} status={}",
                            traceId,
                            source,
                            response.statusCode().value()
                    ));
        }
        return response.bodyToMono(ApiError.class)
                .defaultIfEmpty(new ApiError(
                        ApiErrorCode.REMOTE_ERROR,
                        source + " 调用失败。",
                        Map.of("source", source)
                ))
                .doOnNext(error -> log.warn(
                        "[registration-agent] MCP error trace_id={} source={} status={} code={} message={}",
                        traceId,
                        source,
                        response.statusCode().value(),
                        error.code(),
                        error.message()
                ))
                .flatMap(error -> Mono.error(new RegistrationAgentException(error, source)));
    }
}
