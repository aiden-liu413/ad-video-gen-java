package com.volcengine.demo.advideo.domain.model;

public record Shot(
        String shotId,
        Integer orderNo,
        Integer duration,
        String prompt,
        String action,
        String words,
        String reference,
        String camera,
        String sceneType
) {
}
