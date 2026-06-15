package com.volcengine.demo.advideo.dto;

import java.util.List;

public record GenerationResult(
        String taskId,
        String productName,
        MarketInsight marketInsight,
        DirectorPlan directorPlan,
        Evaluation evaluation,
        MultimediaResult multimedia,
        ReleasePlan releasePlan
) {
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

    public record Evaluation(
            int score,
            List<String> strengths,
            List<String> risks,
            String revisionSuggestion
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
