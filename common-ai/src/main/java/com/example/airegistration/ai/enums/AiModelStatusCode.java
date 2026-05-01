package com.example.airegistration.ai.enums;

public enum AiModelStatusCode {

    CHAT_MODEL_NOT_CONFIGURED(500, "未配置可用的文本模型，请检查 ai.service.model-router.chat。"),
    EMBEDDING_MODEL_NOT_CONFIGURED(500, "未配置可用的向量模型，请检查 ai.service.model-router.embedding。"),
    MODEL_CALL_FAILED(503, "AI 模型调用失败。");

    private final int statusCode;
    private final String message;

    AiModelStatusCode(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }

    public int code() {
        return statusCode;
    }

    public int statusCode() {
        return statusCode;
    }

    public int httpStatus() {
        return statusCode;
    }

    public String message() {
        return message;
    }
}
