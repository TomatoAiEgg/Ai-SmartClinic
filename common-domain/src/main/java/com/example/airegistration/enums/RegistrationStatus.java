package com.example.airegistration.enums;

public enum RegistrationStatus {

    BOOKED("BOOKED", "已预约"),
    CANCELLED("CANCELLED", "已取消"),
    RESCHEDULED("RESCHEDULED", "已改约");

    private final String code;
    private final String description;

    RegistrationStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String code() {
        return code;
    }

    public String description() {
        return description;
    }

    public boolean matches(String value) {
        return value != null && code.equalsIgnoreCase(value.trim());
    }
}
