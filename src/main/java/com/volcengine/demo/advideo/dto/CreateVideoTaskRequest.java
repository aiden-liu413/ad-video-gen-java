package com.volcengine.demo.advideo.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateVideoTaskRequest(
        @NotBlank String inputType,
        String text,
        List<String> imageUrls,
        String videoType,
        String platform,
        Integer duration,
        String aspectRatio,
        String style,
        Integer generateImageCount,
        Integer generateVideoCount
) {
    public int imageCount() {
        return generateImageCount == null || generateImageCount <= 0 ? 2 : generateImageCount;
    }

    public int videoCount() {
        return generateVideoCount == null || generateVideoCount <= 0 ? 1 : generateVideoCount;
    }

    public int durationValue() {
        return duration == null || duration <= 0 ? 30 : duration;
    }

    public String aspectRatioValue() {
        return aspectRatio == null || aspectRatio.isBlank() ? "9:16" : aspectRatio;
    }
}
