package com.volcengine.demo.advideo.dto;

import java.util.List;

public record SelectAssetsRequest(
        List<AssetSelection> selectedImages,
        List<AssetSelection> selectedVideos
) {
    public record AssetSelection(String shotId, String assetId) {
    }
}
