package com.volcengine.demo.advideo.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.demo.advideo.client.ArkChatClient;
import com.volcengine.demo.advideo.dto.CreateVideoTaskRequest;
import com.volcengine.demo.advideo.domain.model.Shot;
import com.volcengine.demo.advideo.service.PromptService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class VideoStoryboardAgent {

    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
    private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+");

    private final ArkChatClient chatClient;
    private final PromptService promptService;
    private final ObjectMapper objectMapper;

    public VideoStoryboardAgent(ArkChatClient chatClient, PromptService promptService, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.promptService = promptService;
        this.objectMapper = objectMapper;
    }

    public StoryboardSummary summarize(CreateVideoTaskRequest request) {
        String userPrompt = """
                请基于用户提供的视频素材，总结成后续可用于广告重制的分镜脚本。
                用户补充需求：%s
                目标平台：%s
                目标总时长：%s秒
                输出分镜数量由你判断，但需要能支撑后续广告视频生成。
                请严格遵守系统提示词中的 JSON 输出格式。
                """.formatted(
                valueOrDefault(request.text(), "未提供"),
                valueOrDefault(request.platform(), "抖音"),
                request.durationValue()
        );

        ArkChatClient.MediaInput mediaInput = StringUtils.hasText(request.sourceVideoFileId())
                ? ArkChatClient.MediaInput.videoFile(request.sourceVideoFileId())
                : ArkChatClient.MediaInput.videoUrl(request.sourceVideoUrl());

        String response = chatClient.completeWithMedia(
                promptService.videoStoryboardAgent(),
                userPrompt,
                List.of(mediaInput)
        );
        return parseStoryboard(response, request.durationValue())
                .orElseGet(() -> fallbackSummary(request.durationValue(), valueOrDefault(request.sourceVideoFileName(), "视频素材分镜")));
    }

    private Optional<StoryboardSummary> parseStoryboard(String response, int totalDuration) {
        if (!StringUtils.hasText(response)) {
            return Optional.empty();
        }
        for (String candidate : jsonCandidates(response)) {
            try {
                JsonNode root = objectMapper.readTree(candidate);
                String title = valueOrDefault(root.path("video_title").asText(), "视频素材分镜总结");
                JsonNode shotList = root.path("shot_list");
                if (!shotList.isArray() || shotList.isEmpty()) {
                    continue;
                }
                List<Shot> shots = new ArrayList<>();
                int index = 1;
                for (JsonNode shot : shotList) {
                    shots.add(new Shot(
                            normalizeShotId(shot.path("id").asText(), index),
                            index,
                            duration(shot),
                            shot.path("image").asText(""),
                            shot.path("action").asText(""),
                            shot.path("words").asText(""),
                            shot.path("reference").asText(""),
                            "",
                            "video_source_summary"
                    ));
                    index += 1;
                }
                return Optional.of(new StoryboardSummary(title, normalizeDurations(shots, totalDuration), candidate));
            } catch (Exception ignored) {
                // Try the next JSON fragment.
            }
        }
        return Optional.empty();
    }

    private List<String> jsonCandidates(String response) {
        List<String> candidates = new ArrayList<>();
        Matcher matcher = JSON_BLOCK.matcher(response);
        while (matcher.find()) {
            candidates.add(matcher.group(1).trim());
        }
        String trimmed = response.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            candidates.add(trimmed.substring(start, end + 1));
        }
        return candidates;
    }

    private StoryboardSummary fallbackSummary(int totalDuration, String title) {
        List<Shot> shots = normalizeDurations(List.of(
                new Shot("shot_001", 1, 5, "视频开场主体与环境建立，清晰交代场景和主角", "镜头平稳推进，建立主体和环境关系", "", "", "", "video_source_summary"),
                new Shot("shot_002", 2, 5, "视频中段关键动作或冲突点总结，突出变化与重点", "镜头跟随主体动作或切换关键细节", "", "", "", "video_source_summary"),
                new Shot("shot_003", 3, 5, "视频结尾结果画面或收束镜头，形成完整叙事闭环", "镜头收束并定格在结果或核心主体", "", "", "", "video_source_summary")
        ), totalDuration);
        return new StoryboardSummary(title, shots, "");
    }

    private List<Shot> normalizeDurations(List<Shot> shots, int totalDuration) {
        if (shots.isEmpty()) {
            return List.of();
        }
        int expected = Math.max(4 * shots.size(), totalDuration);
        int base = expected / shots.size();
        int remainder = expected % shots.size();
        List<Shot> normalized = new ArrayList<>();
        for (int index = 0; index < shots.size(); index++) {
            Shot shot = shots.get(index);
            int seconds = Math.max(4, base + (index < remainder ? 1 : 0));
            normalized.add(new Shot(
                    shot.shotId(),
                    shot.orderNo(),
                    seconds,
                    shot.prompt(),
                    shot.action(),
                    shot.words(),
                    shot.reference(),
                    shot.camera(),
                    shot.sceneType()
            ));
        }
        return normalized;
    }

    private String normalizeShotId(String rawId, int fallbackIndex) {
        if (!StringUtils.hasText(rawId)) {
            return "shot_%03d".formatted(fallbackIndex);
        }
        Matcher matcher = FIRST_NUMBER.matcher(rawId);
        if (matcher.find()) {
            return "shot_%03d".formatted(Integer.parseInt(matcher.group()));
        }
        return "shot_%03d".formatted(fallbackIndex);
    }

    private int duration(JsonNode shot) {
        int seconds = shot.path("seconds").asInt(0);
        if (seconds <= 0) {
            seconds = shot.path("duration").asInt(0);
        }
        return seconds > 0 ? seconds : 5;
    }

    private String valueOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    public record StoryboardSummary(String title, List<Shot> shots, String rawScript) {
    }
}
