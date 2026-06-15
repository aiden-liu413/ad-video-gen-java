package com.volcengine.demo.advideo.domain.enums;

public enum TaskStage {
    CREATED(0),
    MARKET_PLANNING(10),
    SHOT_SCRIPT_GENERATING(20),
    IMAGE_GENERATING(40),
    IMAGE_EVALUATING(50),
    IMAGE_SELECTING(60),
    VIDEO_GENERATING(70),
    VIDEO_EVALUATING(80),
    VIDEO_SELECTING(90),
    FINAL_COMPOSING(95),
    COMPLETED(100),
    FAILED(0);

    private final int progress;

    TaskStage(int progress) {
        this.progress = progress;
    }

    public int progress() {
        return progress;
    }
}
