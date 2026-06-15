package com.volcengine.demo.advideo.domain.model;

public record VideoConfig(
        String videoType,
        ProductInfo productInfo,
        String targetAudience,
        String platform,
        Integer duration,
        String aspectRatio,
        String videoAdvice
) {
}
