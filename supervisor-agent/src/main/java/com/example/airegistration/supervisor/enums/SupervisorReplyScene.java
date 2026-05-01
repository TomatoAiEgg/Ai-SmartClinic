package com.example.airegistration.supervisor.enums;

public enum SupervisorReplyScene {
    HUMAN_REVIEW_REQUIRED("human_review_required"),
    ROUTE_UNCLEAR("route_unclear");

    private final String task;

    SupervisorReplyScene(String task) {
        this.task = task;
    }

    public String task() {
        return task;
    }
}
