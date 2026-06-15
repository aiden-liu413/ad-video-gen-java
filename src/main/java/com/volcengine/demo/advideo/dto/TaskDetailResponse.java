package com.volcengine.demo.advideo.dto;

import com.volcengine.demo.advideo.domain.model.FinalVideo;
import com.volcengine.demo.advideo.domain.model.SelectedImage;
import com.volcengine.demo.advideo.domain.model.SelectedVideo;
import com.volcengine.demo.advideo.domain.model.Shot;
import com.volcengine.demo.advideo.domain.model.ShotImageGroup;
import com.volcengine.demo.advideo.domain.model.ShotVideoGroup;
import com.volcengine.demo.advideo.domain.model.VideoConfig;

import java.time.Instant;
import java.util.List;

public record TaskDetailResponse(
        String taskId,
        String status,
        String stage,
        Integer progress,
        CreateVideoTaskRequest request,
        VideoConfig videoConfig,
        List<Shot> shots,
        List<ShotImageGroup> imageGroups,
        List<ShotImageGroup> scoredImageGroups,
        List<SelectedImage> selectedImages,
        List<ShotVideoGroup> videoGroups,
        List<ShotVideoGroup> scoredVideoGroups,
        List<SelectedVideo> selectedVideos,
        FinalVideo finalVideo,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
