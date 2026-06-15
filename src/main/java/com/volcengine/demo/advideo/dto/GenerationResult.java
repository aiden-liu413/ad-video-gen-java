package com.volcengine.demo.advideo.dto;

import java.util.List;

public final class GenerationResult {
    private GenerationResult() {
    }

    public record MarketInsight(
            String videoType,
            String productName,
            String targetAudience,
            List<String> valuePropositions,
            List<String> keywords,
            String creativeStrategy
    ) {
    }

    public record DirectorPlan(
            String title,
            String script,
            List<Scene> scenes
    ) {
    }

    public record Scene(
            int index,
            String visualPrompt,
            String narration,
            String caption,
            int seconds
    ) {
    }

    public record MultimediaResult(
            String videoUrl,
            String seedanceTaskId,
            List<String> imagePrompts,
            List<String> seedreamImageUrls
    ) {
    }

    public record ReleasePlan(
            String headline,
            String description,
            List<String> hashtags,
            String shortLink
    ) {
    }
}
