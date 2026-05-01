package com.example.airegistration.guide.enums;

public enum GuideReplyScene {
    CONSULTATION("guide_consultation");

    private final String task;

    GuideReplyScene(String task) {
        this.task = task;
    }

    public String task() {
        return task;
    }
}
