package com.volcengine.demo.advideo.domain.model;

public record SelectedImage(
        String shotId,
        ImageCandidate image,
        String prompt,
        String action,
        String words
) {
}
