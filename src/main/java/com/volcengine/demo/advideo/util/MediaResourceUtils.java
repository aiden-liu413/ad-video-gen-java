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
     * 合并图片 URL 和历史 Ark 文件 ID，生成统一的图片资源列表。
     *
     * @param imageUrls 直接可访问的图片 URL 列表，可以为空
     * @param imageFileIds 历史 Ark 文件 ID 列表；如果元素是 HTTP URL，会按 URL 处理
     * @return 去重后的图片资源列表，历史文件 ID 会带上 fileid: 前缀
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
     * 拆分图片资源，便于 URL 资源和历史 Ark 文件 ID 分别传递给模型接口。
     *
     * @param imageUrls 直接可访问的图片 URL 列表，可以为空
     * @param imageFileIds 历史 Ark 文件 ID 列表；如果元素是 HTTP URL，会归入 URL 列表
     * @return 拆分后的图片资源对象，包含 URL 列表和历史文件 ID 列表
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
     * 解析视频 URL，优先使用 S3/RustFS 工作流中的直接访问地址。
     *
     * @param sourceVideoUrl 请求中显式传入的视频 URL
     * @param sourceVideoFileId 历史视频文件 ID；如果是 HTTP URL，会作为视频 URL 使用
     * @return 可直接访问的视频 URL；没有可用 URL 时返回 null
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
     * 解析历史 Ark 视频文件 ID，仅在没有直接视频 URL 时保留旧文件 ID。
     *
     * @param sourceVideoUrl 请求中显式传入的视频 URL
     * @param sourceVideoFileId 历史视频文件 ID
     * @return 可用于 Ark 媒体输入的历史文件 ID；无可用文件 ID 时返回 null
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
