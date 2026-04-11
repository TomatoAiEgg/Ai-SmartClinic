package com.example.airegistration.guide;

import com.example.airegistration.domain.AgentRoute;
import com.example.airegistration.domain.ChatRequest;
import com.example.airegistration.domain.ChatResponse;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class GuideAgentService {

    public Mono<ChatResponse> handle(ChatRequest request) {
        return Mono.just(new ChatResponse(request.chatId(), AgentRoute.GUIDE, answer(request.message()), false,
                Map.of("source", "guide-agent")));
    }

    private String answer(String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (containsAny(text, "address", "location", "地址", "位置", "院区")) {
            return "主院区地址：健康大道 100 号。建议携带有效身份证件，并提前 20 分钟到院签到。";
        }
        if (containsAny(text, "insurance", "medicare", "医保", "社保", "保险")) {
            return "医保资格以现场核验为准。请携带医保卡、身份证和就诊凭证。";
        }
        if (containsAny(text, "cancel", "refund", "退号", "取消", "退款")) {
            return "退号和退款规则需要以实时挂号政策为准。正式写操作前，请先确认当前门诊退号时限。";
        }
        return "可以继续咨询地址、到院时间、医保材料、就诊准备或退号规则。";
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
