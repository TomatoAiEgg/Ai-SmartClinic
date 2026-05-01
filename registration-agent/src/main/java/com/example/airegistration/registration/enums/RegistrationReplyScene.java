package com.example.airegistration.registration.enums;

public enum RegistrationReplyScene {
    CREATE_MISSING_DEPARTMENT("registration_create_missing_department"),
    CREATE_PREVIEW("registration_create_preview"),
    CREATE_RESULT("registration_create_result"),
    QUERY_MISSING_ID("registration_query_missing_id"),
    QUERY_RESULT("registration_query_result"),
    QUERY_LIST("registration_query_list"),
    CANCEL_MISSING_ID("registration_cancel_missing_id"),
    CANCEL_ALREADY_CANCELLED("registration_cancel_already_cancelled"),
    CANCEL_PREVIEW("registration_cancel_preview"),
    CANCEL_RESULT("registration_cancel_result"),
    RESCHEDULE_MISSING_ID("registration_reschedule_missing_id"),
    RESCHEDULE_MISSING_TARGET_TIME("registration_reschedule_missing_target_time"),
    RESCHEDULE_CANCELLED_RECORD("registration_reschedule_cancelled_record"),
    RESCHEDULE_SAME_SLOT("registration_reschedule_same_slot"),
    RESCHEDULE_PREVIEW("registration_reschedule_preview"),
    RESCHEDULE_RESULT("registration_reschedule_result"),
    SLOT_RELEASE_FAILED("slot_release_failed"),
    OLD_SLOT_RELEASE_FAILED("old_slot_release_failed"),
    ERROR("registration_error");

    private final String task;

    RegistrationReplyScene(String task) {
        this.task = task;
    }

    public String task() {
        return task;
    }
}
