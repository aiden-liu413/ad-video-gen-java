package com.volcengine.demo.advideo.agent;

import com.volcengine.demo.advideo.client.ArkChatClient;
import com.volcengine.demo.advideo.dto.GenerateRequest;
import com.volcengine.demo.advideo.dto.GenerationResult.MarketInsight;
import com.volcengine.demo.advideo.service.ProductSourceService;
import com.volcengine.demo.advideo.service.PromptService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class MarketAgent {

    private final ArkChatClient chatClient;
    private final ProductSourceService productSourceService;
    private final PromptService promptService;

    public MarketAgent(ArkChatClient chatClient, ProductSourceService productSourceService, PromptService promptService) {
        this.chatClient = chatClient;
        this.productSourceService = productSourceService;
        this.promptService = promptService;
    }

    public MarketInsight analyze(GenerateRequest request) {
        String audience = valueOrDefault(request.targetAudience(), "25-40 岁、有明确购买需求的城市消费者");
        String productName = productName(request);
        String productDescription = valueOrDefault(request.productDescription(), "用户希望生成商品广告视频");
        String pageSummary = productSourceService.extractPageSummary(request.productUrl());
        List<String> marketReferenceImages = marketReferenceImages(request.referenceImageUrls());
        String prompt = """
                请作为广告市场分析 Agent，为以下产品提炼目标人群、关键词和创意策略。
                产品：%s
                描述：%s
                用户原始需求：%s
                商品链接：%s
                商品图片素材：%s
                页面摘要：%s
                目标人群：%s
                卖点：%s
                """.formatted(
                productName,
                productDescription,
                valueOrDefault(request.prompt(), "未提供"),
                valueOrDefault(request.productUrl(), "未提供"),
                marketReferenceImages.isEmpty()
                        ? "未提供"
                        : marketReferenceImages,
                valueOrDefault(pageSummary, "未提供"),
                audience,
                valueOrDefault(request.sellingPoints(), "未提供")
        );
        String strategy = chatClient.complete(promptService.marketAgent(), prompt);

        return new MarketInsight(
                audience,
                List.of("降低决策成本", "突出差异化卖点", "建立可信任的使用场景"),
                List.of(productName, "限时优惠", "真实体验", "效率提升"),
                strategy
        );
    }

    private String productName(GenerateRequest request) {
        return valueOrDefault(request.productName(), "广告商品");
    }

    private String valueOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
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
}
