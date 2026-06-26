package com.volcengine.demo.advideo.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.demo.advideo.client.ArkChatClient;
import com.volcengine.demo.advideo.dto.GenerateRequest;
import com.volcengine.demo.advideo.dto.GenerationResult.DirectorPlan;
import com.volcengine.demo.advideo.dto.GenerationResult.MarketInsight;
import com.volcengine.demo.advideo.dto.GenerationResult.Scene;
import com.volcengine.demo.advideo.service.PromptService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DirectorAgent {

    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
    private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+");

    private final ArkChatClient chatClient;
    private final PromptService promptService;
    private final ObjectMapper objectMapper;

    public DirectorAgent(ArkChatClient chatClient, PromptService promptService, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.promptService = promptService;
        this.objectMapper = objectMapper;
    }

    public DirectorPlan createPlan(GenerateRequest request, MarketInsight insight, String platform) {
        String style = StringUtils.hasText(request.style()) ? request.style() : "明亮、真实、节奏轻快";
        String productName = StringUtils.hasText(request.productName())
                ? request.productName()
                : StringUtils.hasText(insight.productName()) ? insight.productName() : "广告商品";
        String targetPlatform = StringUtils.hasText(platform) ? platform : "通用短视频平台";
        String platformAdvice = platformAdvice(targetPlatform);
        boolean voiceoverDisabled = request.voiceoverDisabledValue();
        String voiceoverInstruction = voiceoverDisabled
                ? "\n重要：本任务已禁用口播/旁白，所有分镜的 words 字段必须输出空字符串，不得生成任何口播、旁白或字幕文案。"
                : "";
        String script = chatClient.complete(
                promptService.directorStoryboardAgent(),
                """
                        请根据以下信息生成电商广告分镜脚本，具体几段由你根据实际情况决定，不要超过 4。
                        产品：%s
                        用户原始需求：%s
                        期望时长：%s秒
                        发布平台：%s
                        平台脚本要求：%s
                        风格：%s
                        目标人群：%s
                        核心卖点：%s
                        市场策略：%s%s
                        请严格遵守系统提示词中的 JSON 输出格式。
                        """.formatted(
                        productName,
                        request.prompt(),
                        request.duration(),
                        targetPlatform,
                        platformAdvice,
                        style,
                        insight.targetAudience(),
                        String.join("、", insight.valuePropositions()),
                        insight.creativeStrategy(),
                        voiceoverInstruction
                )
        );

        Optional<ParsedStoryboard> parsedStoryboard = parseStoryboard(script);
        List<Scene> scenes = parsedStoryboard.map(ParsedStoryboard::scenes)
                .orElseGet(() -> defaultScenes(style, productName, platformAdvice, voiceoverDisabled));
        if (voiceoverDisabled) {
            scenes = clearSceneWords(scenes);
        }
        String title = parsedStoryboard.map(ParsedStoryboard::title)
                .filter(StringUtils::hasText)
                .orElse(productName + " 场景化广告");
        return new DirectorPlan(title, script, scenes);
    }

    private Optional<ParsedStoryboard> parseStoryboard(String script) {
        if (!StringUtils.hasText(script)) {
            return Optional.empty();
        }
        for (String candidate : jsonCandidates(script)) {
            try {
                JsonNode root = objectMapper.readTree(candidate);
                String title = root.path("video_title").asText("");
                JsonNode shotList = root.path("shot_list");
                if (!shotList.isArray() || shotList.isEmpty()) {
                    continue;
                }
                List<Scene> scenes = new ArrayList<>();
                int index = 1;
                for (JsonNode shot : shotList) {
                    String image = shot.path("image").asText("");
                    String action = shot.path("action").asText("");
                    String words = shot.path("words").asText("");
                    scenes.add(new Scene(
                            shotIndex(shot.path("id").asText(), index),
                            image,
                            action,
                            words,
                            duration(shot)
                    ));
                    index++;
                }
                return Optional.of(new ParsedStoryboard(title, scenes));
            } catch (Exception ignored) {
                // Try the next possible JSON fragment.
            }
        }
        return Optional.empty();
    }

    private List<String> jsonCandidates(String script) {
        List<String> candidates = new ArrayList<>();
        Matcher matcher = JSON_BLOCK.matcher(script);
        while (matcher.find()) {
            candidates.add(matcher.group(1).trim());
        }
        String trimmed = script.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            candidates.add(trimmed.substring(start, end + 1));
        }
        return candidates;
    }

    private List<Scene> defaultScenes(String style, String productName, String platformAdvice, boolean voiceoverDisabled) {
        return List.of(
                new Scene(1,
                        style + "，" + platformAdvice + "，用户遇到典型痛点，镜头聚焦真实生活/工作场景",
                        "你是否也遇到过这样的麻烦？",
                        voiceoverDisabled ? "" : "痛点出现",
                        4),
                new Scene(2,
                        style + "，" + platformAdvice + "，产品以清晰特写出现，展示核心功能和使用动作",
                        productName + "，让复杂问题变简单。",
                        voiceoverDisabled ? "" : "核心卖点",
                        6),
                new Scene(3,
                        style + "，" + platformAdvice + "，用户获得结果，画面给出购买或咨询引导",
                        "现在体验，开启更高效的一天。",
                        voiceoverDisabled ? "" : "立即了解",
                        5)
        );
    }

    private List<Scene> clearSceneWords(List<Scene> scenes) {
        return scenes.stream()
                .map(scene -> new Scene(scene.index(), scene.visualPrompt(), scene.narration(), "", scene.seconds()))
                .toList();
    }

    private String platformAdvice(String platform) {
        String source = platform == null ? "" : platform;
        String normalized = source.toLowerCase();
        if (normalized.contains("douyin") || source.contains("抖音") || source.contains("mobile")) {
            return "前 3 秒强钩子，竖屏近景，字幕短促醒目，节奏快，结尾明确购买引导";
        }
        if (normalized.contains("xiaohongshu") || source.contains("小红书")) {
            return "生活方式种草语气，强调真实体验和细节质感，镜头自然，文案更像使用心得";
        }
        if (normalized.contains("bilibili") || source.contains("b站") || source.contains("哔哩")) {
            return "信息密度更高，讲清楚产品原理和场景对比，允许稍长铺垫但要有清晰结构";
        }
        if (normalized.contains("desktop") || normalized.contains("tv")) {
            return "画面信息更完整，镜头稳定，适合横屏展示产品结构和使用前后对比";
        }
        return "按短视频广告节奏组织，开头给痛点，中段展示卖点，结尾给行动引导";
    }

    private int shotIndex(String id, int fallback) {
        if (!StringUtils.hasText(id)) {
            return fallback;
        }
        Matcher matcher = FIRST_NUMBER.matcher(id);
        return matcher.find() ? Integer.parseInt(matcher.group()) : fallback;
    }

    private int duration(JsonNode shot) {
        int seconds = shot.path("seconds").asInt(0);
        if (seconds <= 0) {
            seconds = shot.path("duration").asInt(0);
        }
        return seconds > 0 ? seconds : 5;
    }

    private record ParsedStoryboard(String title, List<Scene> scenes) {
    }
}
