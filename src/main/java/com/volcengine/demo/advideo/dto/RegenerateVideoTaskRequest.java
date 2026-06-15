package com.volcengine.demo.advideo.dto;

import java.util.List;

public record RegenerateVideoTaskRequest(
        String fromStage,
        List<String> shotIds,
        String reason
) {
}
