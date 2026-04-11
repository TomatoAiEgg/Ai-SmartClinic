package com.example.airegistration.triage;

import com.example.airegistration.domain.AgentRoute;
import com.example.airegistration.domain.ChatRequest;
import com.example.airegistration.domain.ChatResponse;
import com.example.airegistration.domain.DepartmentSuggestion;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class TriageAgentService {

    public Mono<ChatResponse> handle(ChatRequest request) {
        DepartmentSuggestion suggestion = suggestDepartment(request.message());
        String message = suggestion.emergency()
                ? "检测到高风险症状，请直接前往急诊或转人工处理。"
                : "建议挂号科室：%s。原因：%s。这里提供的是分诊建议，不是医疗诊断。"
                .formatted(suggestion.departmentName(), suggestion.reason());
        return Mono.just(new ChatResponse(request.chatId(), AgentRoute.TRIAGE, message, false,
                Map.of("departmentCode", suggestion.departmentCode(),
                        "departmentName", suggestion.departmentName(),
                        "emergency", suggestion.emergency(),
                        "reason", suggestion.reason())));
    }

    private DepartmentSuggestion suggestDepartment(String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (containsAny(text, "chest pain", "shortness of breath", "unconscious", "convulsion", "massive bleeding", "胸痛", "呼吸困难", "意识不清", "抽搐", "大出血")) {
            return new DepartmentSuggestion("ER", "急诊", true, "命中高风险症状规则。");
        }
        if (containsAny(text, "cough", "fever", "throat", "phlegm", "咳嗽", "发热", "咽痛", "痰")) {
            return new DepartmentSuggestion("RESP", "呼吸内科", false, "出现呼吸道或发热相关描述。");
        }
        if (containsAny(text, "stomach", "abdomen", "diarrhea", "vomit", "胃", "腹", "腹泻", "呕吐", "肚子")) {
            return new DepartmentSuggestion("GI", "消化内科", false, "出现消化系统相关症状。");
        }
        if (containsAny(text, "rash", "itch", "skin", "皮疹", "瘙痒", "皮肤")) {
            return new DepartmentSuggestion("DERM", "皮肤科", false, "出现皮肤相关症状。");
        }
        if (containsAny(text, "child", "baby", "kid", "儿童", "小孩", "宝宝")) {
            return new DepartmentSuggestion("PED", "儿科", false, "描述中包含儿童就诊场景。");
        }
        if (containsAny(text, "pregnant", "menstrual", "gyne", "怀孕", "月经", "妇科")) {
            return new DepartmentSuggestion("GYN", "妇科", false, "描述中包含女性健康相关场景。");
        }
        return new DepartmentSuggestion("GEN", "全科医学科", false, "没有命中专科规则，先由全科承接。");
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
