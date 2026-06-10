package com.volcengine.demo.advideo.dto;

public record TaskResponse(
        String taskId,
        String status,
        GenerationResult result,
        String error,
        String currentStep
) {
}
