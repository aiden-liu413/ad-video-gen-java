package com.volcengine.demo.advideo.domain.model;

public record SelectedVideo(
        String shotId,
        Integer duration,
        VideoCandidate video,
        String words,
        String action
) {
}
