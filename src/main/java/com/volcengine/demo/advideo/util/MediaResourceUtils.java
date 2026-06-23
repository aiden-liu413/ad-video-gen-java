package com.volcengine.demo.advideo.util;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class MediaResourceUtils {

    private MediaResourceUtils() {
    }

    public static boolean isHttpUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    /**
     * Merges modern URL inputs with legacy Ark file IDs for image references.
     */
    public static List<String> mergeImageResources(List<String> imageUrls, List<String> imageFileIds) {
        List<String> merged = new ArrayList<>();
        appendUnique(merged, imageUrls);
        if (imageFileIds == null) {
            return merged;
        }
        for (String value : imageFileIds) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (isHttpUrl(value)) {
                appendUnique(merged, List.of(value.trim()));
            } else {
                appendUnique(merged, List.of("fileid:" + value.trim()));
            }
        }
        return merged;
    }

    /**
     * Splits image references so URL-based storage and legacy file IDs can be sent separately.
     */
    public static SplitImageResources splitImageResources(List<String> imageUrls, List<String> imageFileIds) {
        List<String> urls = new ArrayList<>();
        List<String> legacyFileIds = new ArrayList<>();
        appendUnique(urls, imageUrls);
        if (imageFileIds != null) {
            for (String value : imageFileIds) {
                if (!StringUtils.hasText(value)) {
                    continue;
                }
                if (isHttpUrl(value)) {
                    appendUnique(urls, List.of(value.trim()));
                } else {
                    appendUnique(legacyFileIds, List.of(value.trim()));
                }
            }
        }
        return new SplitImageResources(urls, legacyFileIds);
    }

    /**
     * Resolves the video URL preferred by the S3/RustFS workflow.
     */
    public static String resolveVideoUrl(String sourceVideoUrl, String sourceVideoFileId) {
        if (StringUtils.hasText(sourceVideoUrl)) {
            return sourceVideoUrl.trim();
        }
        if (isHttpUrl(sourceVideoFileId)) {
            return sourceVideoFileId.trim();
        }
        return null;
    }

    /**
     * Keeps old Ark file IDs available only when no direct video URL exists.
     */
    public static String resolveLegacyVideoFileId(String sourceVideoUrl, String sourceVideoFileId) {
        if (StringUtils.hasText(sourceVideoUrl)) {
            return null;
        }
        if (StringUtils.hasText(sourceVideoFileId) && !isHttpUrl(sourceVideoFileId)) {
            return sourceVideoFileId.trim();
        }
        return null;
    }

    private static void appendUnique(List<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String normalized = value.trim();
            if (!target.contains(normalized)) {
                target.add(normalized);
            }
        }
    }

    public record SplitImageResources(List<String> imageUrls, List<String> legacyFileIds) {
    }
}
