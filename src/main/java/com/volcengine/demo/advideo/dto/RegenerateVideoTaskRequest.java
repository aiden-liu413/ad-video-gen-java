package com.volcengine.demo.advideo.dto;

import com.volcengine.demo.advideo.domain.model.SelectedImage;
import com.volcengine.demo.advideo.domain.model.SelectedVideo;
import com.volcengine.demo.advideo.domain.model.Shot;
import com.volcengine.demo.advideo.domain.model.VideoConfig;

import java.util.List;

public record RegenerateVideoTaskRequest(
        String fromStage,
        List<String> shotIds,
        String reason,
        CreateVideoTaskRequest taskInput,
        VideoConfig videoConfig,
        List<Shot> shots,
        List<SelectedImage> selectedImages,
        List<SelectedVideo> selectedVideos
) {
}
