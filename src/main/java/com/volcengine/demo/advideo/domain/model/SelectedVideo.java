package com.volcengine.demo.advideo.domain.model;

public record SelectedVideo(
        String shotId,
        VideoCandidate video,
        String words,
        String action
) {
}
