package com.volcengine.demo.advideo.domain.model;

import java.util.List;

public record FinalVideo(
        String videoUrl,
        String videoTitle,
        String videoRelease,
        List<String> hashtags,
        List<SelectedVideo> selectedVideos
) {
}
