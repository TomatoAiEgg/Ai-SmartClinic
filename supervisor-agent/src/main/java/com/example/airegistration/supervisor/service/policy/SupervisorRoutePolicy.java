package com.example.airegistration.supervisor.service.policy;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.enums.AgentRoute;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SupervisorRoutePolicy {

    private static final String[] RED_FLAG_KEYWORDS = {
            "chest pain", "shortness of breath", "unconscious", "convulsion", "massive bleeding", "high fever",
            "胸痛", "呼吸困难", "昏迷", "抽搐", "大出血", "高烧"
    };

    private static final String[] REGISTRATION_KEYWORDS = {
            "register", "appointment", "book", "reschedule", "cancel", "slot", "doctor", "clinic",
            "挂号", "预约", "约号", "改约", "取消", "号源",
            "医生", "门诊"
    };

    private static final String[] GUIDE_KEYWORDS = {
            "rule", "policy", "process", "address", "parking", "insurance", "refund", "material",
            "规则", "政策", "流程", "材料", "地址", "院区",
            "停车", "医保", "退费", "退款", "怎么取号",
            "怎么签到", "在哪里", "怎么走", "需要带什么",
            "注意事项"
    };

    private static final String[] TRIAGE_HINT_KEYWORDS = {
            "which department", "what department", "which clinic", "what clinic", "who should i see",
            "挂什么科", "挂哪个科", "看什么科", "看哪个科",
            "什么科室", "哪个科室", "应该挂", "该挂",
            "需要挂什么", "适合挂"
    };

    private static final String[] TRIAGE_SYMPTOM_KEYWORDS = {
            "symptom", "symptoms", "condition", "illness", "cough", "fever", "rash", "stomach", "pain",
            "child", "pregnant", "headache", "abdomen",
            "症状", "咳嗽", "发烧", "发热", "皮疹", "胃痛",
            "肚子痛", "小孩", "怀孕", "头痛", "腹痛", "病情"
    };

    private static final String[] TRIAGE_REGISTRATION_HANDOFF_KEYWORDS = {
            "describe symptoms", "describing symptoms", "by symptoms", "by describing my symptoms",
            "based on symptoms", "based on my condition", "based on illness",
            "描述症状", "描述病情", "说病情", "通过说病情",
            "通过描述症状", "通过描述病情", "根据症状",
            "根据病情", "按症状"
    };

    public AgentRoute determineRoute(ChatRequest request) {
        AgentRoute actionRoute = determineRouteFromAction(request.metadata().get("action"));
        if (actionRoute != null) {
            return actionRoute;
        }

        String text = request.message() == null ? "" : request.message().toLowerCase(Locale.ROOT);
        if (containsAny(text, RED_FLAG_KEYWORDS)) {
            return AgentRoute.HUMAN_REVIEW;
        }
        if (looksLikeSymptomBasedRegistrationRequest(text)) {
            return AgentRoute.TRIAGE;
        }
        if (looksLikeTriageQuestion(text)) {
            return AgentRoute.TRIAGE;
        }
        if (looksLikeGuideQuestion(text)) {
            return AgentRoute.GUIDE;
        }
        if (looksLikeRegistrationRequest(text)) {
            return AgentRoute.REGISTRATION;
        }
        if (containsAny(text, TRIAGE_SYMPTOM_KEYWORDS)) {
            return AgentRoute.TRIAGE;
        }
        return AgentRoute.GUIDE;
    }

    private boolean looksLikeRegistrationRequest(String text) {
        if (containsRegistrationLanguage(text)) {
            return true;
        }
        if (containsAny(text, "挂", "约") && containsAny(text, "号", "医生", "门诊")) {
            return true;
        }
        return containsAny(text,
                "想挂", "我要挂", "帮我挂", "给我挂", "需要挂")
                && containsAny(text, "科", "内科", "外科", "儿科", "妇科");
    }

    private boolean looksLikeGuideQuestion(String text) {
        if (containsAny(text, GUIDE_KEYWORDS)) {
            return true;
        }
        return containsAny(text, "取消", "退号")
                && containsAny(text, "规则", "政策", "退费", "退款", "流程", "说明");
    }

    private boolean looksLikeTriageQuestion(String text) {
        if (!containsAny(text, TRIAGE_HINT_KEYWORDS)) {
            return false;
        }
        return containsSymptomContext(text);
    }

    private boolean looksLikeSymptomBasedRegistrationRequest(String text) {
        return containsRegistrationLanguage(text)
                && (containsSymptomContext(text) || containsAny(text, TRIAGE_REGISTRATION_HANDOFF_KEYWORDS));
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

    private boolean containsRegistrationLanguage(String text) {
        return containsAny(text, REGISTRATION_KEYWORDS);
    }

    private boolean containsSymptomContext(String text) {
        return containsAny(text, TRIAGE_SYMPTOM_KEYWORDS);
    }
}
