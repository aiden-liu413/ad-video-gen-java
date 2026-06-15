package com.volcengine.demo.advideo.dto;

import com.volcengine.demo.advideo.domain.model.Shot;
import com.volcengine.demo.advideo.domain.model.ShotImageGroup;
import com.volcengine.demo.advideo.domain.model.ShotVideoGroup;

import java.util.List;

public record UpdateWorkflowContextRequest(
        List<Shot> shots,
        List<ShotImageGroup> scoredImageGroups,
        List<ShotVideoGroup> scoredVideoGroups
) {
}
