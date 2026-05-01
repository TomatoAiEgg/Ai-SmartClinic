package com.example.airegistration.supervisor.service.orchestrator;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.supervisor.service.RouteDecision;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SupervisorOrchestrationPolicy {

    private static final String[] REGISTRATION_KEYWORDS = {
            "register", "appointment", "book", "slot", "doctor", "clinic",
            "挂号", "预约", "约号", "号源",
            "医生", "门诊", "想挂", "帮我挂",
            "给我挂", "我要挂"
    };

    private static final String[] SYMPTOM_KEYWORDS = {
            "symptom", "symptoms", "condition", "illness", "cough", "fever",
            "rash", "pain", "headache", "stomach", "abdomen",
            "症状", "病情", "咳嗽", "发烧",
            "发热", "皮疹", "疼", "痛", "头痛",
            "胃痛", "肚子痛", "腹痛"
    };

    private static final String[] TRIAGE_HANDOFF_KEYWORDS = {
            "based on symptoms", "by symptoms", "describe symptoms", "describing symptoms",
            "根据症状", "根据病情", "按症状",
            "描述症状", "描述病情", "说病情"
    };

    public boolean shouldTriageThenRegistration(ChatRequest request, RouteDecision decision) {
        if (decision == null || decision.route() != AgentRoute.TRIAGE || hasExplicitAction(request)) {
            return false;
        }
        String text = normalizedMessage(request);
        return containsAny(text, REGISTRATION_KEYWORDS)
                && (containsAny(text, SYMPTOM_KEYWORDS) || containsAny(text, TRIAGE_HANDOFF_KEYWORDS));
    }

    private boolean hasExplicitAction(ChatRequest request) {
        return request.metadata().containsKey("action")
                && request.metadata().get("action") != null
                && !request.metadata().get("action").isBlank();
    }

    private String normalizedMessage(ChatRequest request) {
        return request.message() == null ? "" : request.message().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
