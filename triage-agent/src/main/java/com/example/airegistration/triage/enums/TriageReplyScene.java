package com.example.airegistration.triage.enums;

public enum TriageReplyScene {
    DEPARTMENT_SUGGESTION("triage_department_suggestion");

    private final String task;

    TriageReplyScene(String task) {
        this.task = task;
    }

    public String task() {
        return task;
    }
}
