package com.volcengine.demo.advideo.dto;

import java.time.Instant;

public record TaskSummary(
        String taskId,
        String status,
        String currentStep,
        String productName,
        Instant createdAt,
        Instant updatedAt
) {
}
