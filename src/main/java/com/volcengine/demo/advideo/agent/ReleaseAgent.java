package com.volcengine.demo.advideo.agent;

import com.volcengine.demo.advideo.client.ArkChatClient;
import com.volcengine.demo.advideo.dto.GenerateRequest;
import com.volcengine.demo.advideo.dto.GenerationResult.MultimediaResult;
import com.volcengine.demo.advideo.dto.GenerationResult.ReleasePlan;
import com.volcengine.demo.advideo.service.PromptService;
import com.volcengine.demo.advideo.service.ShortLinkService;
import org.springframework.stereotype.Service;

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

    public ReleasePlan createReleasePlan(GenerateRequest request, MultimediaResult multimedia) {
        String productName = request.productName() == null || request.productName().isBlank() ? "广告商品" : request.productName();
        String landingUrl = request.landingPageUrl() == null || request.landingPageUrl().isBlank()
                ? multimedia.videoUrl()
                : request.landingPageUrl();
        String shortLink = shortLinkService.createShortLink(landingUrl);
        String copy = chatClient.complete(
                promptService.releaseAgent(),
                "请为 " + productName + " 生成可发布的短视频广告文案，视频地址：" + multimedia.videoUrl()
        );
        return new ReleasePlan(
                productName + "，把好体验带到眼前",
                copy,
                List.of("#" + productName, "#新品体验", "#效率提升"),
                shortLink
        );
    }
}
