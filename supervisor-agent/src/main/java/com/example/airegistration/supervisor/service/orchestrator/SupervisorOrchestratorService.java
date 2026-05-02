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
        log.info("[supervisor] route decided trace_id={} chat_id={} user_id={} route={} target_agent={} source={} rule_route={} model_route={} confidence={} safety_level={} reason={} message_length={} metadata_keys={}",
                request.traceId(),
                request.chatId(),
                request.userId(),
                route,
                decision.targetAgent(),
                decision.source(),
                decision.ruleRoute(),
                decision.modelRoute(),
                decision.confidence(),
                decision.safetyLevel(),
                decision.reason(),
                request.message() == null ? 0 : request.message().length(),
                request.metadata().keySet());
        if (route == AgentRoute.HUMAN_REVIEW) {
            return replyService.reply(request, AgentRoute.HUMAN_REVIEW, SupervisorReplyScene.HUMAN_REVIEW_REQUIRED,
                    Map.of("reason", "red_flag_symptom"));
        }
        if (orchestrationPolicy.shouldTriageThenRegistration(request, decision)) {
            return triageThenRegistration(request, decision);
        }

        return agentClient.call(route, request)
                .onErrorResume(IllegalArgumentException.class,
                        error -> replyService.reply(request, route, SupervisorReplyScene.ROUTE_UNCLEAR,
                                Map.of("reason", error.getMessage())));
    }

    private Mono<ChatResponse> triageThenRegistration(ChatRequest request, RouteDecision decision) {
        log.info("[supervisor] orchestration started trace_id={} chat_id={} mode=TRIAGE_THEN_REGISTRATION",
                request.traceId(),
                request.chatId());
        return agentClient.call(AgentRoute.TRIAGE, request)
                .flatMap(triageResponse -> {
                    if (isEmergency(triageResponse)) {
                        Map<String, String> handoffMetadata = SupervisorHandoffMetadata.triageToRegistration(
                                triageResponse,
                                SupervisorHandoffMetadata.STATUS_TRIAGE_EMERGENCY_STOP
                        );
                        log.info("[supervisor] orchestration stopped by triage emergency trace_id={} chat_id={}",
                                request.traceId(),
                                request.chatId());
                        return Mono.just(withOrchestrationData(
                                triageResponse,
                                SupervisorHandoffMetadata.STATUS_TRIAGE_EMERGENCY_STOP,
                                triageResponse,
                                handoffMetadata
                        ));
                    }

                    String departmentCode = stringValue(triageResponse.data().get("departmentCode"));
                    if (isBlank(departmentCode)) {
                        Map<String, String> handoffMetadata = SupervisorHandoffMetadata.triageToRegistration(
                                triageResponse,
                                SupervisorHandoffMetadata.STATUS_TRIAGE_NO_DEPARTMENT
                        );
                        log.info("[supervisor] orchestration stopped because triage returned no department trace_id={} chat_id={}",
                                request.traceId(),
                                request.chatId());
                        return Mono.just(withOrchestrationData(
                                triageResponse,
                                SupervisorHandoffMetadata.STATUS_TRIAGE_NO_DEPARTMENT,
                                triageResponse,
                                handoffMetadata
                        ));
                    }

                    Map<String, String> handoffMetadata = SupervisorHandoffMetadata.triageToRegistration(
                            triageResponse,
                            SupervisorHandoffMetadata.STATUS_REGISTRATION_HANDOFF
                    );
                    RouteDecision handoffDecision = decision.withHandoffMetadata(handoffMetadata);
                    ChatRequest registrationRequest = registrationRequest(request, handoffDecision);
                    log.info("[supervisor] orchestration handoff trace_id={} chat_id={} from=TRIAGE to=REGISTRATION department_code={} handoff_keys={}",
                            request.traceId(),
                            request.chatId(),
                            departmentCode,
                            handoffDecision.handoffMetadata().keySet());
                    return agentClient.call(AgentRoute.REGISTRATION, registrationRequest)
                            .map(registrationResponse -> withOrchestrationData(
                                    registrationResponse,
                                    SupervisorHandoffMetadata.STATUS_REGISTRATION_HANDOFF,
                                    triageResponse,
                                    handoffMetadata
                            ));
                });
    }

    private ChatRequest registrationRequest(ChatRequest request, RouteDecision handoffDecision) {
        Map<String, String> handoffMetadata = handoffDecision.handoffMetadata();
        Map<String, String> metadata = new HashMap<>(request.metadata());
        metadata.putIfAbsent("action", "create");
        metadata.put("orchestration", SupervisorHandoffMetadata.MODE_TRIAGE_THEN_REGISTRATION);
        metadata.putAll(handoffMetadata);
        putIfText(metadata, "departmentCode", SupervisorHandoffMetadata.departmentCode(handoffMetadata));
        putIfText(metadata, "departmentName", SupervisorHandoffMetadata.departmentName(handoffMetadata));
        putIfText(metadata, "triageEmergency", SupervisorHandoffMetadata.emergency(handoffMetadata));
        putIfText(metadata, "triageReason", SupervisorHandoffMetadata.triageReason(handoffMetadata));
        return new ChatRequest(request.chatId(), request.userId(), request.message(), Map.copyOf(metadata), request.traceId());
    }

    private ChatResponse withOrchestrationData(ChatResponse response,
                                               String status,
                                               ChatResponse triageResponse,
                                               Map<String, String> handoffMetadata) {
        Map<String, Object> data = new HashMap<>(response.data());
        data.put("orchestration", Map.of(
                "mode", SupervisorHandoffMetadata.MODE_TRIAGE_THEN_REGISTRATION,
                "status", status,
                "upstreamRoute", AgentRoute.TRIAGE.name(),
                "handoff", SupervisorHandoffMetadata.responseSummary(handoffMetadata)
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
