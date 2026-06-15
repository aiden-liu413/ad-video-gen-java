package com.volcengine.demo.advideo.agent;

import com.volcengine.demo.advideo.client.ArkChatClient;
import com.volcengine.demo.advideo.dto.GenerateRequest;
import com.volcengine.demo.advideo.dto.GenerationResult.MultimediaResult;
import com.volcengine.demo.advideo.dto.GenerationResult.ReleasePlan;
import com.volcengine.demo.advideo.service.PromptService;
import com.volcengine.demo.advideo.service.ShortLinkService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ReleaseAgent {

    private final ArkChatClient chatClient;
    private final ShortLinkService shortLinkService;
    private final PromptService promptService;

    public ReleaseAgent(ArkChatClient chatClient, ShortLinkService shortLinkService, PromptService promptService) {
        this.chatClient = chatClient;
        this.shortLinkService = shortLinkService;
        this.promptService = promptService;
    }

    public ReleasePlan createReleasePlan(GenerateRequest request, MultimediaResult multimedia, String platform) {
        String productName = request.productName() == null || request.productName().isBlank() ? "广告商品" : request.productName();
        String targetPlatform = StringUtils.hasText(platform) ? platform : "通用短视频平台";
        String releaseAdvice = releaseAdvice(targetPlatform);
        String shortLink = shortLinkService.createShortLink(multimedia.videoUrl());
        String copy = chatClient.complete(
                promptService.releaseAgent(),
                """
                        请根据以下信息生成可发布的短视频广告文案。
                        商品：%s
                        发布平台：%s
                        平台文案要求：%s
                        视频地址：%s
                        请直接输出文案正文。
                        """.formatted(productName, targetPlatform, releaseAdvice, multimedia.videoUrl())
        );
        return new ReleasePlan(
                headline(productName, targetPlatform),
                copy,
                hashtags(productName, targetPlatform),
                shortLink
        );
    }

    private String releaseAdvice(String platform) {
        String source = platform == null ? "" : platform;
        String normalized = source.toLowerCase();
        if (normalized.contains("douyin") || source.contains("抖音") || source.contains("mobile")) {
            return "标题要短、有冲突或痛点；正文前三句直接给利益点；话题偏热门转化标签；CTA 明确";
        }
        if (normalized.contains("xiaohongshu") || source.contains("小红书")) {
            return "标题像真实种草笔记；正文强调体验、避坑、适用人群；话题偏生活方式和搜索关键词";
        }
        if (normalized.contains("bilibili") || source.contains("b站") || source.contains("哔哩")) {
            return "标题说明看点；正文可以讲清楚测试过程、对比结论和适合人群；语气更理性";
        }
        if (normalized.contains("desktop") || normalized.contains("tv")) {
            return "文案更适合品牌展示和落地页承接，强调产品卖点、结果证明和购买路径";
        }
        return "标题突出核心卖点，正文包含痛点、解决方案、适用人群和行动引导";
    }

    private String headline(String productName, String platform) {
        String source = platform == null ? "" : platform;
        String normalized = source.toLowerCase();
        if (normalized.contains("xiaohongshu") || source.contains("小红书")) {
            return productName + "真实体验，谁用谁知道";
        }
        if (normalized.contains("bilibili") || source.contains("b站") || source.contains("哔哩")) {
            return productName + "实测：核心卖点和适用场景一次讲清";
        }
        if (normalized.contains("douyin") || source.contains("抖音") || source.contains("mobile")) {
            return productName + "，这个体验太省心了";
        }
        return productName + "，把好体验带到眼前";
    }

    private List<String> hashtags(String productName, String platform) {
        String source = platform == null ? "" : platform;
        String normalized = source.toLowerCase();
        if (normalized.contains("xiaohongshu") || source.contains("小红书")) {
            return List.of("#" + productName, "#真实测评", "#好物分享", "#生活方式");
        }
        if (normalized.contains("bilibili") || source.contains("b站") || source.contains("哔哩")) {
            return List.of("#" + productName, "#实测", "#产品体验", "#选购指南");
        }
        if (normalized.contains("douyin") || source.contains("抖音") || source.contains("mobile")) {
            return List.of("#" + productName, "#好物推荐", "#省心体验", "#限时了解");
        }
        return List.of("#" + productName, "#新品体验", "#效率提升");
    }
}
