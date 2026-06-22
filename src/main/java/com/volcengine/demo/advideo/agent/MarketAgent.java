package com.volcengine.demo.advideo.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.demo.advideo.client.ArkChatClient;
import com.volcengine.demo.advideo.dto.GenerateRequest;
import com.volcengine.demo.advideo.dto.GenerationResult.MarketInsight;
import com.volcengine.demo.advideo.service.PromptService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class MarketAgent {

    private static final java.util.regex.Pattern JSON_BLOCK = java.util.regex.Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
    private static final String DEFAULT_VIDEO_TYPE = "商品展示视频";

    private final ArkChatClient chatClient;
    private final PromptService promptService;
    private final ObjectMapper objectMapper;

    public MarketAgent(ArkChatClient chatClient, PromptService promptService, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.promptService = promptService;
        this.objectMapper = objectMapper;
    }

    public MarketInsight analyze(GenerateRequest request) {
        String audience = valueOrDefault(request.targetAudience(), "25-40 岁、有明确购买需求的城市消费者");
        String productName = productName(request);
        String productDescription = valueOrDefault(request.productDescription(), "用户希望生成商品广告视频");
        List<String> marketReferenceImages = marketReferenceImages(request.referenceImageUrls());
        List<String> marketReferenceImageFileIds = marketReferenceImageFileIds(request.referenceImageFileIds());
        String prompt = """
                请根据以下输入生成电商营销视频策划 JSON。
                产品：%s
                描述：%s
                用户原始需求：%s
                商品图片素材：%s
                商品图片素材文件ID：%s
                目标人群：%s
                卖点：%s
                请严格遵守系统提示词中的输出格式。
                """.formatted(
                productName,
                productDescription,
                valueOrDefault(request.prompt(), "未提供"),
                marketReferenceImages.isEmpty()
                        ? "未提供"
                        : marketReferenceImages,
                marketReferenceImageFileIds.isEmpty()
                        ? "未提供"
                        : marketReferenceImageFileIds,
                audience,
                valueOrDefault(request.sellingPoints(), "未提供")
        );
        String strategy = chatClient.complete(promptService.marketAgent(), prompt, marketReferenceImages, marketReferenceImageFileIds);
        ParsedMarketInsight parsed = parseMarketInsight(strategy, productName, audience);

        return new MarketInsight(
                parsed.videoType(),
                parsed.productName(),
                parsed.targetAudience(),
                parsed.valuePropositions(),
                parsed.keywords(),
                parsed.videoAdvice()
        );
    }

    private ParsedMarketInsight parseMarketInsight(String response, String fallbackProductName, String fallbackAudience) {
        for (String candidate : jsonCandidates(response)) {
            try {
                JsonNode root = objectMapper.readTree(candidate);
                JsonNode productInfo = root.path("product_info");
                String name = valueOrDefault(productInfo.path("name").asText(), fallbackProductName);
                String sellingPoint = valueOrDefault(productInfo.path("selling_point").asText(), "根据商品图片和用户描述提炼核心卖点，用于突出购买理由");
                String audience = valueOrDefault(productInfo.path("audience").asText(), fallbackAudience);
                String videoType = normalizeVideoType(root.path("video_type").asText());
                String videoAdvice = valueOrDefault(root.path("video_advice").asText(), response);
                return new ParsedMarketInsight(
                        videoType,
                        name,
                        audience,
                        List.of(sellingPoint),
                        List.of(name),
                        videoAdvice
                );
            } catch (Exception ignored) {
                // Try the next JSON-looking fragment.
            }
        }
        return new ParsedMarketInsight(
                DEFAULT_VIDEO_TYPE,
                fallbackProductName,
                fallbackAudience,
                List.of("根据商品图片和用户描述提炼核心卖点，用于突出购买理由"),
                List.of(fallbackProductName, "限时优惠", "真实体验", "效率提升"),
                valueOrDefault(response, "")
        );
    }

    private List<String> jsonCandidates(String response) {
        if (!StringUtils.hasText(response)) {
            return List.of();
        }
        java.util.ArrayList<String> candidates = new java.util.ArrayList<>();
        java.util.regex.Matcher matcher = JSON_BLOCK.matcher(response);
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

    private String productName(GenerateRequest request) {
        return valueOrDefault(request.productName(), "广告商品");
    }

    private String valueOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String normalizeVideoType(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_VIDEO_TYPE;
        }
        String text = value.trim();
        if (text.contains("对比") || text.contains("测评")) {
            return "对比测评视频";
        }
        if (text.contains("功能") || text.contains("演示") || text.contains("说明")) {
            return "功能演示视频";
        }
        if (text.contains("种草") || text.contains("场景") || text.contains("生活方式")) {
            return "场景种草视频";
        }
        return DEFAULT_VIDEO_TYPE;
    }

    private List<String> marketReferenceImages(List<String> referenceImageUrls) {
        if (referenceImageUrls == null || referenceImageUrls.isEmpty()) {
            return List.of();
        }
        return referenceImageUrls.stream()
                .filter(StringUtils::hasText)
                .filter(url -> !url.startsWith("data:image/"))
                .toList();
    }

    private List<String> marketReferenceImageFileIds(List<String> referenceImageFileIds) {
        if (referenceImageFileIds == null || referenceImageFileIds.isEmpty()) {
            return List.of();
        }
        return referenceImageFileIds.stream()
                .filter(StringUtils::hasText)
                .toList();
    }

    private record ParsedMarketInsight(
            String videoType,
            String productName,
            String targetAudience,
            List<String> valuePropositions,
            List<String> keywords,
            String videoAdvice
    ) {
    }
}
