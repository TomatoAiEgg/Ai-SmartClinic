package com.example.airegistration.enums;

public enum ApiErrorCode {

    INVALID_REQUEST(400, "请求参数不合法。"),
    UNAUTHORIZED(401, "登录状态无效，请重新登录。"),
    NOT_FOUND(404, "资源不存在。"),
    REQUIRES_CONFIRMATION(400, "该操作需要用户明确确认。"),
    REMOTE_ERROR(503, "远程服务调用失败。"),
    INTERNAL_ERROR(500, "系统内部异常。");

    private final int code;
    private final String message;

    ApiErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public int httpStatus() {
        return code;
    }

    public String message() {
        return message;
    }
}
