package com.volcengine.demo.advideo.domain.model;

import java.util.List;

public record ShotVideoGroup(
        String shotId,
        String prompt,
        String action,
        String words,
        String reference,
        List<VideoCandidate> videos
) {
}
