package com.example.airegistration.supervisor.service.orchestrator;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.supervisor.client.AgentClient;
import com.example.airegistration.supervisor.enums.SupervisorReplyScene;
import com.example.airegistration.supervisor.service.RouteDecision;
import com.example.airegistration.supervisor.service.SupervisorReplyService;
import com.example.airegistration.supervisor.service.SupervisorRouteClassifier;
import com.example.airegistration.supervisor.service.SupervisorRoutingUseCase;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class SupervisorOrchestratorService implements SupervisorRoutingUseCase {

    private static final Logger log = LoggerFactory.getLogger(SupervisorOrchestratorService.class);

    private final SupervisorRouteClassifier routeClassifier;
    private final AgentClient agentClient;
    private final SupervisorReplyService replyService;
    private final SupervisorOrchestrationPolicy orchestrationPolicy;

    public SupervisorOrchestratorService(SupervisorRouteClassifier routeClassifier,
                                         AgentClient agentClient,
                                         SupervisorReplyService replyService,
                                         SupervisorOrchestrationPolicy orchestrationPolicy) {
        this.routeClassifier = routeClassifier;
        this.agentClient = agentClient;
        this.replyService = replyService;
        this.orchestrationPolicy = orchestrationPolicy;
    }

    @Override
    public Mono<ChatResponse> route(ChatRequest request) {
        return routeClassifier.determineRoute(request)
                .flatMap(decision -> route(request, decision));
    }

    private Mono<ChatResponse> route(ChatRequest request, RouteDecision decision) {
        AgentRoute route = decision.route();
        log.info("[supervisor] route decided trace_id={} chat_id={} user_id={} route={} source={} rule_route={} model_route={} confidence={} reason={} message_length={} metadata_keys={}",
                request.traceId(),
                request.chatId(),
                request.userId(),
                route,
                decision.source(),
                decision.ruleRoute(),
                decision.modelRoute(),
                decision.confidence(),
                decision.reason(),
                request.message() == null ? 0 : request.message().length(),
                request.metadata().keySet());
        if (route == AgentRoute.HUMAN_REVIEW) {
            return replyService.reply(request, AgentRoute.HUMAN_REVIEW, SupervisorReplyScene.HUMAN_REVIEW_REQUIRED,
                    Map.of("reason", "red_flag_symptom"));
        }
        if (orchestrationPolicy.shouldTriageThenRegistration(request, decision)) {
            return triageThenRegistration(request);
        }

        return switch (route) {
            case TRIAGE -> agentClient.callTriage(request);
            case REGISTRATION -> agentClient.callRegistration(request);
            case GUIDE -> agentClient.callGuide(request);
            default -> replyService.reply(request, route, SupervisorReplyScene.ROUTE_UNCLEAR, Map.of());
        };
    }

    private Mono<ChatResponse> triageThenRegistration(ChatRequest request) {
        log.info("[supervisor] orchestration started trace_id={} chat_id={} mode=TRIAGE_THEN_REGISTRATION",
                request.traceId(),
                request.chatId());
        return agentClient.callTriage(request)
                .flatMap(triageResponse -> {
                    if (isEmergency(triageResponse)) {
                        log.info("[supervisor] orchestration stopped by triage emergency trace_id={} chat_id={}",
                                request.traceId(),
                                request.chatId());
                        return Mono.just(withOrchestrationData(
                                triageResponse,
                                "TRIAGE_THEN_REGISTRATION",
                                "triage_emergency_stop",
                                triageResponse
                        ));
                    }

                    String departmentCode = stringValue(triageResponse.data().get("departmentCode"));
                    if (isBlank(departmentCode)) {
                        log.info("[supervisor] orchestration stopped because triage returned no department trace_id={} chat_id={}",
                                request.traceId(),
                                request.chatId());
                        return Mono.just(withOrchestrationData(
                                triageResponse,
                                "TRIAGE_THEN_REGISTRATION",
                                "triage_no_department",
                                triageResponse
                        ));
                    }

                    ChatRequest registrationRequest = registrationRequest(request, triageResponse);
                    log.info("[supervisor] orchestration handoff trace_id={} chat_id={} from=TRIAGE to=REGISTRATION department_code={}",
                            request.traceId(),
                            request.chatId(),
                            departmentCode);
                    return agentClient.callRegistration(registrationRequest)
                            .map(registrationResponse -> withOrchestrationData(
                                    registrationResponse,
                                    "TRIAGE_THEN_REGISTRATION",
                                    "registration_handoff",
                                    triageResponse
                            ));
                });
    }

    private ChatRequest registrationRequest(ChatRequest request, ChatResponse triageResponse) {
        Map<String, String> metadata = new HashMap<>(request.metadata());
        metadata.putIfAbsent("action", "create");
        metadata.put("orchestration", "TRIAGE_THEN_REGISTRATION");
        putIfText(metadata, "departmentCode", stringValue(triageResponse.data().get("departmentCode")));
        putIfText(metadata, "departmentName", stringValue(triageResponse.data().get("departmentName")));
        putIfText(metadata, "triageEmergency", stringValue(triageResponse.data().get("emergency")));
        putIfText(metadata, "triageReason", stringValue(triageResponse.data().get("reason")));
        return new ChatRequest(request.chatId(), request.userId(), request.message(), Map.copyOf(metadata), request.traceId());
    }

    private ChatResponse withOrchestrationData(ChatResponse response,
                                               String mode,
                                               String status,
                                               ChatResponse triageResponse) {
        Map<String, Object> data = new HashMap<>(response.data());
        data.put("orchestration", Map.of(
                "mode", mode,
                "status", status,
                "upstreamRoute", AgentRoute.TRIAGE.name()
        ));
        data.put("upstreamTriage", Map.copyOf(triageResponse.data()));
        return new ChatResponse(
                response.chatId(),
                response.route(),
                response.message(),
                response.requiresConfirmation(),
                Map.copyOf(data)
        );
    }

    private boolean isEmergency(ChatResponse triageResponse) {
        Object value = triageResponse.data().get("emergency");
        return value instanceof Boolean emergency
                ? emergency
                : "true".equalsIgnoreCase(stringValue(value));
    }

    private void putIfText(Map<String, String> metadata, String key, String value) {
        if (!isBlank(value)) {
            metadata.put(key, value.trim());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
