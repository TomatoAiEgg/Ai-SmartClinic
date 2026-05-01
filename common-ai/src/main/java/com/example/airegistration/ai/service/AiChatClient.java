package com.example.airegistration.ai.service;

import com.example.airegistration.ai.dto.AiChatRequest;
import com.example.airegistration.ai.dto.AiChatResult;
import com.example.airegistration.ai.dto.FallbackChatResult;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.StringUtils;

public class AiChatClient {

    private static final Logger log = LoggerFactory.getLogger(AiChatClient.class);

    private final FallbackChatClient fallbackChatClient;

    public AiChatClient(FallbackChatClient fallbackChatClient) {
        this.fallbackChatClient = fallbackChatClient;
    }

    public AiChatResult call(AiChatRequest request) {
        FallbackChatResult result = fallbackChatClient.call(toPrompt(request));
        String content = normalize(result.content());
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("AI chat response is empty");
        }
        log.info("[ai-chat] operation={} model={} attempt={} attributes={}",
                request.operation(),
                result.model(),
                result.attempt(),
                request.attributes().keySet());
        return new AiChatResult(content, result.model(), result.attempt());
    }

    public String callText(AiChatRequest request) {
        return call(request).content();
    }

    private Prompt toPrompt(AiChatRequest request) {
        List<Message> messages = new ArrayList<>();
        if (StringUtils.hasText(request.systemPrompt())) {
            messages.add(new SystemMessage(request.systemPrompt()));
        }
        request.messages().stream()
                .filter(StringUtils::hasText)
                .map(UserMessage::new)
                .forEach(messages::add);
        if (StringUtils.hasText(request.userPrompt())) {
            messages.add(new UserMessage(request.userPrompt()));
        }
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("AI chat request must contain systemPrompt, userPrompt or messages");
        }
        return new Prompt(messages);
    }

    private String normalize(String content) {
        if (content == null) {
            return "";
        }
        return content.trim();
    }
}
