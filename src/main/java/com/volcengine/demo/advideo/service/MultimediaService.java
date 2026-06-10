package com.volcengine.demo.advideo.service;

import com.volcengine.demo.advideo.client.SeedanceVideoClient;
import com.volcengine.demo.advideo.client.SeedanceVideoClient.VideoGeneration;
import com.volcengine.demo.advideo.client.SeedreamImageClient;
import com.volcengine.demo.advideo.dto.GenerateRequest;
import com.volcengine.demo.advideo.dto.GenerationCheckpoint;
import com.volcengine.demo.advideo.dto.GenerationResult.DirectorPlan;
import com.volcengine.demo.advideo.dto.GenerationResult.MultimediaResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MultimediaService {

    private static final Logger log = LoggerFactory.getLogger(MultimediaService.class);
    private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+");

    private final SeedreamImageClient seedreamImageClient;
    private final SeedanceVideoClient seedanceVideoClient;
    private final PromptService promptService;

    public MultimediaService(
            SeedreamImageClient seedreamImageClient,
            SeedanceVideoClient seedanceVideoClient,
            PromptService promptService
    ) {
        this.seedreamImageClient = seedreamImageClient;
        this.seedanceVideoClient = seedanceVideoClient;
        this.promptService = promptService;
    }

    public MultimediaResult generate(GenerateRequest request, DirectorPlan plan) {
        return generate(request, plan, new GenerationCheckpoint());
    }

    public MultimediaResult generate(GenerateRequest request, DirectorPlan plan, GenerationCheckpoint checkpoint) {
        return generate(request, plan, checkpoint, ignored -> {
        });
    }

    public MultimediaResult generate(
            GenerateRequest request,
            DirectorPlan plan,
            GenerationCheckpoint checkpoint,
            Consumer<GenerationCheckpoint> checkpointSaver
    ) {
        List<String> imagePrompts = checkpoint.getImagePrompts();
        if (imagePrompts == null) {
            imagePrompts = plan.scenes().stream()
                    .map(scene -> promptService.directorImageAgent()
                            + "\n\n# 当前分镜\n"
                            + "shot_id: shot_" + scene.index() + "\n"
                            + "prompt: " + scene.visualPrompt() + "\n"
                            + "action: " + scene.narration() + "\n"
                            + "words: " + scene.caption())
                    .toList();
            checkpoint.setImagePrompts(imagePrompts);
            checkpointSaver.accept(checkpoint);
            log.info("Multimedia step: generated image prompts, count={}", imagePrompts.size());
        } else {
            log.info("Multimedia step: reuse image prompts checkpoint, count={}", imagePrompts.size());
        }

        List<String> imageUrls = checkpoint.getSeedreamImageUrls();
        if (imageUrls == null) {
            log.info("Multimedia step: Seedream images start, promptCount={}", imagePrompts.size());
            imageUrls = seedreamImageClient.generateImages(imagePrompts, request.referenceImageUrls());
            checkpoint.setSeedreamImageUrls(imageUrls);
            checkpointSaver.accept(checkpoint);
            log.info("Multimedia step: Seedream images ready, count={}", imageUrls.size());
        } else {
            log.info("Multimedia step: reuse Seedream image checkpoint, count={}", imageUrls.size());
        }

        String videoPrompt = promptService.directorVideoAgent()
                + "\n\n# 分镜脚本\n"
                + plan.script();
        VideoGeneration video = seedanceVideoClient.generateVideo(
                productName(request),
                imageUrls,
                videoPrompt,
                durationSeconds(request.duration())
        );
        log.info("Multimedia step: Seedance video ready, seedanceTaskId={}, videoUrl={}", video.taskId(), video.videoUrl());
        return new MultimediaResult(video.videoUrl(), video.taskId(), imagePrompts, imageUrls);
    }

    private String productName(GenerateRequest request) {
        return request.productName() == null || request.productName().isBlank() ? "广告商品" : request.productName();
    }

    private int durationSeconds(String duration) {
        if (duration == null || duration.isBlank()) {
            return 5;
        }
        Matcher matcher = FIRST_NUMBER.matcher(duration);
        if (!matcher.find()) {
            return 5;
        }
        int parsed = Integer.parseInt(matcher.group());
        return Math.max(1, Math.min(parsed, 30));
    }
}
