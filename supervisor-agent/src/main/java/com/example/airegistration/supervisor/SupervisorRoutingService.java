package com.example.airegistration.supervisor;

import com.example.airegistration.domain.AgentRoute;
import com.example.airegistration.domain.ChatRequest;
import com.example.airegistration.domain.ChatResponse;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class SupervisorRoutingService {

    private final WebClient triageClient;
    private final WebClient registrationClient;
    private final WebClient guideClient;

    public SupervisorRoutingService(WebClient.Builder webClientBuilder,
                                    @Value("${app.agents.triage.base-url}") String triageBaseUrl,
                                    @Value("${app.agents.registration.base-url}") String registrationBaseUrl,
                                    @Value("${app.agents.guide.base-url}") String guideBaseUrl) {
        this.triageClient = webClientBuilder.baseUrl(triageBaseUrl).build();
        this.registrationClient = webClientBuilder.baseUrl(registrationBaseUrl).build();
        this.guideClient = webClientBuilder.baseUrl(guideBaseUrl).build();
    }

    public Mono<ChatResponse> route(ChatRequest request) {
        AgentRoute route = determineRoute(request);
        if (route == AgentRoute.HUMAN_REVIEW) {
            return Mono.just(new ChatResponse(
                    request.chatId(),
                    AgentRoute.HUMAN_REVIEW,
                    "检测到胸痛、呼吸困难等高风险症状，请直接前往急诊或联系人工客服。",
                    false,
                    Map.of("reason", "red_flag_symptom")
            ));
        }

        return switch (route) {
            case TRIAGE -> callAgent(triageClient, "/api/triage", request);
            case REGISTRATION -> callAgent(registrationClient, "/api/registration", request);
            case GUIDE -> callAgent(guideClient, "/api/guide", request);
            default -> Mono.just(new ChatResponse(
                    request.chatId(),
                    route,
                    "当前没有匹配到可用处理路由。",
                    false,
                    Map.of()
            ));
        };
    }

    private AgentRoute determineRoute(ChatRequest request) {
        AgentRoute actionRoute = determineRouteFromAction(request.metadata().get("action"));
        if (actionRoute != null) {
            return actionRoute;
        }

        String text = request.message() == null ? "" : request.message().toLowerCase(Locale.ROOT);
        if (containsAny(text,
                "chest pain", "shortness of breath", "unconscious", "convulsion", "massive bleeding", "high fever",
                "胸痛", "呼吸困难", "意识不清", "抽搐", "大出血", "高烧")) {
            return AgentRoute.HUMAN_REVIEW;
        }
        if (containsAny(text,
                "register", "appointment", "book", "reschedule", "cancel", "slot", "doctor", "clinic",
                "挂号", "挂", "预约", "改约", "取消", "号源", "医生", "门诊", "单号")) {
            return AgentRoute.REGISTRATION;
        }
        if (containsAny(text,
                "cough", "fever", "rash", "stomach", "pain", "child", "pregnant",
                "咳嗽", "发热", "皮疹", "胃痛", "肚子疼", "儿童", "孕妇", "症状")) {
            return AgentRoute.TRIAGE;
        }
        return AgentRoute.GUIDE;
    }

    private AgentRoute determineRouteFromAction(String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        return switch (action.trim().toLowerCase(Locale.ROOT)) {
            case "create", "query", "cancel", "reschedule" -> AgentRoute.REGISTRATION;
            default -> null;
        };
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private Mono<ChatResponse> callAgent(WebClient client, String path, ChatRequest request) {
        return client.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatResponse.class);
    }
}
