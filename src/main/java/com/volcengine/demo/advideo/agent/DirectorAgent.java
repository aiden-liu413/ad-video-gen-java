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
    private static final Pattern SHOT_BLOCK = Pattern.compile("分镜\\s*(\\d+)\\s*[：:]([\\s\\S]*?)(?=\\n\\s*分镜\\s*\\d+\\s*[：:]|\\z)");
    private static final Pattern TITLE = Pattern.compile("视频标题\\s*[：:]\\s*(.+)");
    private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+");

    private final ArkChatClient chatClient;
    private final PromptService promptService;
    private final ObjectMapper objectMapper;

    public DirectorAgent(ArkChatClient chatClient, PromptService promptService, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.promptService = promptService;
        this.objectMapper = objectMapper;
    }

    public DirectorPlan createPlan(GenerateRequest request, MarketInsight insight) {
        String style = StringUtils.hasText(request.style()) ? request.style() : "明亮、真实、节奏轻快";
        String productName = StringUtils.hasText(request.productName()) ? request.productName() : "广告商品";
        String script = chatClient.complete(
                promptService.directorStoryboardAgent(),
                """
                        请为产品广告生成多段式短视频脚本 具体几段由你根据实际情况决定 不要超过 3。
                        产品：%s
                        用户原始需求：%s
                        期望时长：%s
                        风格：%s
                        市场策略：%s
                        请输出可被后续多媒体步骤直接使用的分镜脚本，字段包含 id、image、action、reference、words。
                        """.formatted(productName, request.prompt(), request.duration(), style, insight.creativeStrategy())
        );

        List<Scene> scenes = parseScenes(script).orElseGet(() -> defaultScenes(style, productName));
        String title = parseTitle(script).orElse(productName + " 场景化广告");
        return new DirectorPlan(title, script, scenes);
    }

    private Optional<List<Scene>> parseScenes(String script) {
        if (!StringUtils.hasText(script)) {
            return Optional.empty();
        }
        Optional<List<Scene>> jsonScenes = parseJsonScenes(script);
        if (jsonScenes.isPresent()) {
            return jsonScenes;
        }
        List<Scene> textScenes = parseTextScenes(script);
        return textScenes.isEmpty() ? Optional.empty() : Optional.of(textScenes);
    }

    private Optional<List<Scene>> parseJsonScenes(String script) {
        for (String candidate : jsonCandidates(script)) {
            try {
                JsonNode root = objectMapper.readTree(candidate);
                JsonNode shotList = root.path("shot_list");
                if (!shotList.isArray() || shotList.isEmpty()) {
                    continue;
                }
                List<Scene> scenes = new ArrayList<>();
                int index = 1;
                for (JsonNode shot : shotList) {
                    String image = text(shot, "image", "prompt");
                    String action = text(shot, "action", "video_prompt");
                    String words = text(shot, "words", "caption");
                    scenes.add(new Scene(
                            shotIndex(shot.path("id").asText(), index),
                            image,
                            action,
                            words,
                            duration(shot)
                    ));
                    index++;
                }
                return Optional.of(scenes);
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

    private List<Scene> parseTextScenes(String script) {
        List<Scene> scenes = new ArrayList<>();
        Matcher matcher = SHOT_BLOCK.matcher(script);
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            String block = matcher.group(2);
            String image = field(block, "image", "画面", "首帧图画面").orElse("");
            String action = field(block, "action", "动作", "运镜").orElse("");
            String words = field(block, "words", "口播", "台词", "文案").orElse("");
            if (StringUtils.hasText(image) || StringUtils.hasText(action) || StringUtils.hasText(words)) {
                scenes.add(new Scene(index, image, action, words, 5));
            }
        }
        return scenes;
    }

    private Optional<String> field(String block, String... names) {
        for (String name : names) {
            Pattern pattern = Pattern.compile("(?m)^\\s*" + Pattern.quote(name) + "\\s*[：:]\\s*(.+)$");
            Matcher matcher = pattern.matcher(block);
            if (matcher.find() && StringUtils.hasText(matcher.group(1))) {
                return Optional.of(matcher.group(1).trim());
            }
        }
        return Optional.empty();
    }

    private Optional<String> parseTitle(String script) {
        if (!StringUtils.hasText(script)) {
            return Optional.empty();
        }
        Matcher matcher = TITLE.matcher(script);
        if (matcher.find() && StringUtils.hasText(matcher.group(1))) {
            return Optional.of(matcher.group(1).trim());
        }
        return Optional.empty();
    }

    private List<Scene> defaultScenes(String style, String productName) {
        return List.of(
                new Scene(1,
                        style + "，用户遇到典型痛点，镜头聚焦真实生活/工作场景",
                        "你是否也遇到过这样的麻烦？",
                        "痛点出现",
                        4),
                new Scene(2,
                        style + "，产品以清晰特写出现，展示核心功能和使用动作",
                        productName + "，让复杂问题变简单。",
                        "核心卖点",
                        6),
                new Scene(3,
                        style + "，用户获得结果，画面给出购买或咨询引导",
                        "现在体验，开启更高效的一天。",
                        "立即了解",
                        5)
        );
    }

    private String text(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = node.path(fieldName).asText("");
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
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
}
