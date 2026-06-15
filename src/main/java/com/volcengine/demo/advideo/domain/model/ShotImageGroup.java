package com.volcengine.demo.advideo.domain.model;

import java.util.List;

public record ShotImageGroup(
        String shotId,
        Integer duration,
        String prompt,
        String action,
        String words,
        String reference,
        List<ImageCandidate> images
) {
}
