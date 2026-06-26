package com.volcengine.demo.advideo.orchestrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.demo.advideo.agent.DirectorAgent;
import com.volcengine.demo.advideo.agent.MarketAgent;
import com.volcengine.demo.advideo.agent.ReleaseAgent;
import com.volcengine.demo.advideo.agent.VideoStoryboardAgent;
import com.volcengine.demo.advideo.client.ArkChatClient;
import com.volcengine.demo.advideo.client.SeedanceVideoClient;
import com.volcengine.demo.advideo.client.SeedreamImageClient;
import com.volcengine.demo.advideo.domain.enums.TaskStage;
import com.volcengine.demo.advideo.domain.enums.TaskStatus;
import com.volcengine.demo.advideo.domain.model.FinalVideo;
import com.volcengine.demo.advideo.domain.model.ImageCandidate;
import com.volcengine.demo.advideo.domain.model.ProductInfo;
import com.volcengine.demo.advideo.domain.model.SelectedImage;
import com.volcengine.demo.advideo.domain.model.SelectedVideo;
import com.volcengine.demo.advideo.domain.model.Shot;
import com.volcengine.demo.advideo.domain.model.ShotImageGroup;
import com.volcengine.demo.advideo.domain.model.ShotVideoGroup;
import com.volcengine.demo.advideo.domain.model.VideoCandidate;
import com.volcengine.demo.advideo.domain.model.VideoConfig;
import com.volcengine.demo.advideo.dto.CreateVideoTaskRequest;
import com.volcengine.demo.advideo.dto.GenerateRequest;
import com.volcengine.demo.advideo.dto.GenerationResult.DirectorPlan;
import com.volcengine.demo.advideo.dto.GenerationResult.MarketInsight;
import com.volcengine.demo.advideo.dto.GenerationResult.MultimediaResult;
import com.volcengine.demo.advideo.dto.GenerationResult.ReleasePlan;
import com.volcengine.demo.advideo.dto.RegenerateVideoTaskRequest;
import com.volcengine.demo.advideo.dto.SelectAssetsRequest;
import com.volcengine.demo.advideo.dto.TaskDetailResponse;
import com.volcengine.demo.advideo.dto.TaskSummary;
import com.volcengine.demo.advideo.dto.UpdateWorkflowContextRequest;
import com.volcengine.demo.advideo.util.MediaResourceUtils;
import com.volcengine.demo.advideo.entity.VideoTaskContextEntity;
import com.volcengine.demo.advideo.entity.VideoTaskEntity;
import com.volcengine.demo.advideo.repository.VideoTaskContextRepository;
import com.volcengine.demo.advideo.repository.VideoTaskRepository;
import com.volcengine.demo.advideo.service.FfmpegComposeService;
import com.volcengine.demo.advideo.service.PromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class WorkflowOrchestratorServiceImpl implements WorkflowOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowOrchestratorServiceImpl.class);
    private static final java.util.regex.Pattern JSON_BLOCK = java.util.regex.Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
    private static final String WORKFLOW_PRODUCT_IMAGE_AD = "product_image_ad";
    private static final String WORKFLOW_VIDEO_STORYBOARD_AD = "video_storyboard_ad";

    private final VideoTaskRepository taskRepository;
    private final VideoTaskContextRepository contextRepository;
    private final ObjectMapper objectMapper;
    private final MarketAgent marketAgent;
    private final DirectorAgent directorAgent;
    private final VideoStoryboardAgent videoStoryboardAgent;
    private final SeedreamImageClient imageClient;
    private final SeedanceVideoClient videoClient;
    private final ReleaseAgent releaseAgent;
    private final FfmpegComposeService ffmpegComposeService;
    private final ArkChatClient chatClient;
    private final PromptService promptService;

    public WorkflowOrchestratorServiceImpl(
            VideoTaskRepository taskRepository,
            VideoTaskContextRepository contextRepository,
            ObjectMapper objectMapper,
            MarketAgent marketAgent,
            DirectorAgent directorAgent,
            VideoStoryboardAgent videoStoryboardAgent,
            SeedreamImageClient imageClient,
            SeedanceVideoClient videoClient,
            ReleaseAgent releaseAgent,
            FfmpegComposeService ffmpegComposeService,
            ArkChatClient chatClient,
            PromptService promptService
    ) {
        this.taskRepository = taskRepository;
        this.contextRepository = contextRepository;
        this.objectMapper = objectMapper;
        this.marketAgent = marketAgent;
        this.directorAgent = directorAgent;
        this.videoStoryboardAgent = videoStoryboardAgent;
        this.imageClient = imageClient;
        this.videoClient = videoClient;
        this.releaseAgent = releaseAgent;
        this.ffmpegComposeService = ffmpegComposeService;
        this.chatClient = chatClient;
        this.promptService = promptService;
    }

    @Override
    @Transactional
    public String createTask(CreateVideoTaskRequest request) {
        String taskId = "task_" + UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();
        String workflowType = workflowType(request);

        VideoTaskEntity task = new VideoTaskEntity();
        task.setTaskId(taskId);
        task.setInputType(request.inputType());
        task.setInputText(request.text());
        task.setVideoType(valueOrDefault(request.videoType(), "商品展示视频"));
        task.setPlatform(valueOrDefault(request.platform(), "抖音"));
        task.setDuration(request.durationValue());
        task.setAspectRatio(request.aspectRatioValue());
        task.setStyle(request.style());
        task.setStatus(TaskStatus.CREATED.name());
        task.setStage(TaskStage.CREATED.name());
        task.setProgress(TaskStage.CREATED.progress());
        task.setRequestJson(toJson(request));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskRepository.save(task);

        WorkflowContext context = new WorkflowContext();
        context.setTaskId(taskId);
        VideoTaskContextEntity contextEntity = new VideoTaskContextEntity();
        contextEntity.setTaskId(taskId);
        contextEntity.setContextJson(toJson(context));
        contextEntity.setCreatedAt(now);
        contextEntity.setUpdatedAt(now);
        contextRepository.save(contextEntity);

        log.info("Created video task, taskId={}, workflowType={}, inputType={}, videoType={}",
                taskId, workflowType, request.inputType(), task.getVideoType());
        return taskId;
    }

    @Override
    public void startAsync(String taskId) {
        advanceAsync(taskId);
    }

    @Override
    public void run(String taskId) {
        while (true) {
            advanceOneStep(taskId);
            VideoTaskEntity task = taskRepository.findByTaskId(taskId).orElseThrow();
            if (TaskStatus.SUCCESS.name().equals(task.getStatus()) || TaskStatus.FAILED.name().equals(task.getStatus())) {
                return;
            }
        }
    }

    @Override
    public void advanceAsync(String taskId) {
        CompletableFuture.runAsync(() -> advanceOneStep(taskId))
                .exceptionally(ex -> {
                    log.error("Advance video task async failed, taskId={}", taskId, ex);
                    return null;
                });
    }

    private void advanceOneStep(String taskId) {
        VideoTaskEntity task = taskRepository.findByTaskId(taskId).orElseThrow();
        CreateVideoTaskRequest request = requestFromTask(task);
        WorkflowContext context = loadContext(taskId);
        String workflowType = workflowType(request);
        try {
            if (isVideoStoryboardWorkflow(workflowType) && isEmpty(context.getShots())) {
                markStage(taskId, TaskStage.SHOT_SCRIPT_GENERATING);
                VideoStoryboardAgent.StoryboardSummary summary = videoStoryboardAgent.summarize(request);
                context.setSourceStoryboardTitle(summary.title());
                context.setVideoConfig(generateVideoConfigForVideoStoryboard(request, summary));
                context.setShots(applyVoiceoverPolicy(request, summary.shots()));
                saveContext(context);
                if (shouldPauseForReview(request, TaskStage.SHOT_SCRIPT_GENERATING, false)) {
                    markWaitingReview(taskId, TaskStage.SHOT_SCRIPT_GENERATING);
                    return;
                }
            }

            if (!isVideoStoryboardWorkflow(workflowType) && context.getVideoConfig() == null) {
                markStage(taskId, TaskStage.MARKET_PLANNING);
                context.setVideoConfig(generateVideoConfig(request));
                saveContext(context);
                if (shouldPauseForReview(request, TaskStage.MARKET_PLANNING, false)) {
                    markWaitingReview(taskId, TaskStage.MARKET_PLANNING);
                    return;
                }
            }

            if (!isVideoStoryboardWorkflow(workflowType) && isEmpty(context.getShots())) {
                markStage(taskId, TaskStage.SHOT_SCRIPT_GENERATING);
                context.setShots(generateShots(request, context.getVideoConfig()));
                saveContext(context);
                if (shouldPauseForReview(request, TaskStage.SHOT_SCRIPT_GENERATING, false)) {
                    markWaitingReview(taskId, TaskStage.SHOT_SCRIPT_GENERATING);
                    return;
                }
            }

            if (isEmpty(context.getImageGroups())) {
                markStage(taskId, TaskStage.IMAGE_GENERATING);
                List<ShotImageGroup> imageGroups = generateImages(request, context.getVideoConfig(), context.getShots());
                context.setImageGroups(imageGroups);
                if (request.imageScoringEnabledValue()) {
                    context.setScoredImageGroups(evaluateImages(imageGroups));
                    context.setSelectedImages(pickBestImages(context.getScoredImageGroups()));
                } else {
                    context.setScoredImageGroups(List.of());
                    context.setSelectedImages(List.of());
                }
                saveContext(context);
                if (shouldPauseForReview(request, TaskStage.IMAGE_GENERATING, !request.imageScoringEnabledValue())) {
                    markWaitingReview(taskId, TaskStage.IMAGE_GENERATING);
                    return;
                }
            }

            if (request.imageScoringEnabledValue() && isEmpty(context.getScoredImageGroups())) {
                markStage(taskId, TaskStage.IMAGE_GENERATING);
                context.setScoredImageGroups(evaluateImages(context.getImageGroups()));
                context.setSelectedImages(pickBestImages(context.getScoredImageGroups()));
                saveContext(context);
                if (shouldPauseForReview(request, TaskStage.IMAGE_GENERATING, false)) {
                    markWaitingReview(taskId, TaskStage.IMAGE_GENERATING);
                    return;
                }
            }

            if (isEmpty(context.getVideoGroups())) {
                if (isEmpty(context.getSelectedImages())) {
                    if (request.imageScoringEnabledValue()) {
                        context.setSelectedImages(pickBestImages(context.getScoredImageGroups()));
                        saveContext(context);
                    } else {
                        context.setSelectedImages(pickFirstImages(context.getImageGroups()));
                        saveContext(context);
                    }
                }
                if (isEmpty(context.getSelectedImages())) {
                    if (request.imageScoringEnabledValue()) {
                        markWaitingReview(taskId, TaskStage.IMAGE_GENERATING);
                        return;
                    } else {
                        markWaitingReview(taskId, TaskStage.IMAGE_GENERATING);
                        return;
                    }
                }
                markStage(taskId, TaskStage.VIDEO_GENERATING);
                List<ShotVideoGroup> videoGroups = generateVideos(request, context.getVideoConfig(), context.getSelectedImages());
                context.setVideoGroups(videoGroups);
                if (request.videoScoringEnabledValue()) {
                    context.setScoredVideoGroups(evaluateVideos(videoGroups));
                    context.setSelectedVideos(pickBestVideos(context.getScoredVideoGroups()));
                } else {
                    context.setScoredVideoGroups(List.of());
                    context.setSelectedVideos(List.of());
                }
                saveContext(context);
                if (shouldPauseForReview(request, TaskStage.VIDEO_GENERATING, !request.videoScoringEnabledValue())) {
                    markWaitingReview(taskId, TaskStage.VIDEO_GENERATING);
                    return;
                }
            }

            if (request.videoScoringEnabledValue() && isEmpty(context.getScoredVideoGroups())) {
                markStage(taskId, TaskStage.VIDEO_GENERATING);
                context.setScoredVideoGroups(evaluateVideos(context.getVideoGroups()));
                context.setSelectedVideos(pickBestVideos(context.getScoredVideoGroups()));
                saveContext(context);
                if (shouldPauseForReview(request, TaskStage.VIDEO_GENERATING, false)) {
                    markWaitingReview(taskId, TaskStage.VIDEO_GENERATING);
                    return;
                }
            }

            if (context.getFinalVideo() == null) {
                if (isEmpty(context.getSelectedVideos())) {
                    if (request.videoScoringEnabledValue()) {
                        context.setSelectedVideos(pickBestVideos(context.getScoredVideoGroups()));
                        saveContext(context);
                    } else {
                        context.setSelectedVideos(pickFirstVideos(context.getVideoGroups()));
                        saveContext(context);
                    }
                }
                if (isEmpty(context.getSelectedVideos())) {
                    markWaitingReview(taskId, TaskStage.VIDEO_GENERATING);
                    return;
                }
                markStage(taskId, TaskStage.FINAL_COMPOSING);
                context.setFinalVideo(composeFinalVideo(taskId, request, context.getVideoConfig(), context.getSelectedVideos()));
                saveContext(context);
            }

            markSuccess(taskId, context.getFinalVideo().videoUrl());
        } catch (RuntimeException ex) {
            log.error("Advance video task failed, taskId={}, stage={}", taskId, task.getStage(), ex);
            markFailed(taskId, ex);
            throw ex;
        }
    }

    @Override
    @Transactional
    public void retryFromStage(String taskId, String stage) {
        clearFromStage(taskId, TaskStage.valueOf(stage));
        advanceAfterCommit(taskId);
    }

    @Override
    @Transactional
    public void regenerate(String taskId, RegenerateVideoTaskRequest request) {
        TaskStage fromStage = request == null || !StringUtils.hasText(request.fromStage())
                ? TaskStage.IMAGE_GENERATING
                : TaskStage.valueOf(request.fromStage());
        applyRegenerateInputs(taskId, fromStage, request);
        clearFromStage(taskId, fromStage);
        advanceAfterCommit(taskId);
    }

    private void applyRegenerateInputs(String taskId, TaskStage fromStage, RegenerateVideoTaskRequest request) {
        if (request == null) {
            return;
        }
        if (request.taskInput() != null) {
            updateTaskInput(taskId, request.taskInput());
        }
        WorkflowContext context = loadContext(taskId);
        boolean changed = false;
        if (fromStage.ordinal() >= TaskStage.SHOT_SCRIPT_GENERATING.ordinal()
                && request.videoConfig() != null) {
            context.setVideoConfig(request.videoConfig());
            changed = true;
        }
        if (fromStage.ordinal() >= TaskStage.SHOT_SCRIPT_GENERATING.ordinal()
                && request.shots() != null) {
            context.setShots(mergeEditableShotFields(context.getShots(), request.shots()));
            changed = true;
        }
        if (fromStage.ordinal() >= TaskStage.VIDEO_GENERATING.ordinal()
                && request.selectedImages() != null) {
            List<ShotImageGroup> sourceGroups = isEmpty(context.getScoredImageGroups())
                    ? context.getImageGroups()
                    : context.getScoredImageGroups();
            context.setSelectedImages(orderSelectedImages(sourceGroups, request.selectedImages()));
            changed = true;
        }
        if (fromStage.ordinal() >= TaskStage.FINAL_COMPOSING.ordinal()
                && request.selectedVideos() != null) {
            List<ShotVideoGroup> sourceGroups = isEmpty(context.getScoredVideoGroups())
                    ? context.getVideoGroups()
                    : context.getScoredVideoGroups();
            context.setSelectedVideos(orderSelectedVideos(sourceGroups, request.selectedVideos()));
            changed = true;
        }
        if (changed) {
            saveContext(context);
        }
    }

    private void updateTaskInput(String taskId, CreateVideoTaskRequest request) {
        VideoTaskEntity task = taskRepository.findByTaskId(taskId).orElseThrow();
        CreateVideoTaskRequest merged = mergeTaskInput(requestFromTask(task), request);
        task.setInputType(valueOrDefault(merged.inputType(), task.getInputType()));
        task.setInputText(merged.text());
        task.setVideoType(valueOrDefault(merged.videoType(), task.getVideoType()));
        task.setPlatform(merged.platform());
        task.setDuration(merged.duration());
        task.setAspectRatio(merged.aspectRatio());
        task.setStyle(merged.style());
        task.setRequestJson(toJson(merged));
        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);
        log.info("Update task input for regeneration, taskId={}, fromInputType={}, duration={}, aspectRatio={}",
                taskId, merged.inputType(), merged.duration(), merged.aspectRatio());
    }

    private CreateVideoTaskRequest mergeTaskInput(CreateVideoTaskRequest current, CreateVideoTaskRequest patch) {
        if (patch == null) {
            return current;
        }
        return new CreateVideoTaskRequest(
                valueOrDefault(patch.workflowType(), current.workflowType()),
                valueOrDefault(patch.inputType(), current.inputType()),
                patch.text() == null ? current.text() : patch.text(),
                patch.imageUrls() == null ? current.imageUrls() : patch.imageUrls(),
                patch.imageFileIds() == null ? current.imageFileIds() : patch.imageFileIds(),
                patch.sourceVideoUrl() == null ? current.sourceVideoUrl() : patch.sourceVideoUrl(),
                patch.sourceVideoFileId() == null ? current.sourceVideoFileId() : patch.sourceVideoFileId(),
                patch.sourceVideoFileName() == null ? current.sourceVideoFileName() : patch.sourceVideoFileName(),
                patch.videoType() == null ? current.videoType() : patch.videoType(),
                patch.platform() == null ? current.platform() : patch.platform(),
                patch.duration() == null ? current.duration() : patch.duration(),
                patch.aspectRatio() == null ? current.aspectRatio() : patch.aspectRatio(),
                patch.style() == null ? current.style() : patch.style(),
                patch.imageScoringEnabled() == null ? current.imageScoringEnabled() : patch.imageScoringEnabled(),
                patch.videoScoringEnabled() == null ? current.videoScoringEnabled() : patch.videoScoringEnabled(),
                patch.autoConfirmEnabled() == null ? current.autoConfirmEnabled() : patch.autoConfirmEnabled(),
                patch.voiceoverDisabled() == null ? current.voiceoverDisabled() : patch.voiceoverDisabled(),
                patch.generateImageCount() == null ? current.generateImageCount() : patch.generateImageCount(),
                patch.generateVideoCount() == null ? current.generateVideoCount() : patch.generateVideoCount()
        );
    }

    @Override
    @Transactional
    public void selectAssets(String taskId, SelectAssetsRequest request) {
        WorkflowContext context = loadContext(taskId);
        if (request.selectedImages() != null && !request.selectedImages().isEmpty()) {
            List<ShotImageGroup> sourceGroups = isEmpty(context.getScoredImageGroups())
                    ? context.getImageGroups()
                    : context.getScoredImageGroups();
            context.setSelectedImages(selectImages(sourceGroups, request.selectedImages()));
        }
        if (request.selectedVideos() != null && !request.selectedVideos().isEmpty()) {
            List<ShotVideoGroup> sourceGroups = isEmpty(context.getScoredVideoGroups())
                    ? context.getVideoGroups()
                    : context.getScoredVideoGroups();
            context.setSelectedVideos(selectVideos(sourceGroups, request.selectedVideos()));
        }
        saveContext(context);
    }

    @Override
    @Transactional
    public void updateContext(String taskId, UpdateWorkflowContextRequest request) {
        WorkflowContext context = loadContext(taskId);
        VideoTaskEntity task = taskRepository.findByTaskId(taskId).orElseThrow();
        TaskStage currentStage = TaskStage.valueOf(task.getStage());
        if (request.shots() != null) {
            context.setShots(mergeEditableShotFields(context.getShots(), request.shots()));
            context.setImageGroups(List.of());
            context.setScoredImageGroups(List.of());
            context.setSelectedImages(List.of());
            context.setVideoGroups(List.of());
            context.setScoredVideoGroups(List.of());
            context.setSelectedVideos(List.of());
            context.setFinalVideo(null);
            markWaitingReview(taskId, currentStage);
        }
        if (request.scoredImageGroups() != null) {
            context.setScoredImageGroups(request.scoredImageGroups());
            context.setSelectedImages(pickSelectedOrBestImages(request.scoredImageGroups()));
            context.setVideoGroups(List.of());
            context.setScoredVideoGroups(List.of());
            context.setSelectedVideos(List.of());
            context.setFinalVideo(null);
            markWaitingReview(taskId, TaskStage.IMAGE_GENERATING);
        }
        if (request.selectedImages() != null) {
            List<ShotImageGroup> sourceGroups = isEmpty(context.getScoredImageGroups())
                    ? context.getImageGroups()
                    : context.getScoredImageGroups();
            context.setSelectedImages(orderSelectedImages(sourceGroups, request.selectedImages()));
            context.setVideoGroups(List.of());
            context.setScoredVideoGroups(List.of());
            context.setSelectedVideos(List.of());
            context.setFinalVideo(null);
            markWaitingReview(taskId, TaskStage.VIDEO_GENERATING);
        }
        if (request.scoredVideoGroups() != null) {
            context.setScoredVideoGroups(request.scoredVideoGroups());
            context.setSelectedVideos(pickSelectedOrBestVideos(request.scoredVideoGroups()));
            context.setFinalVideo(null);
            markWaitingReview(taskId, TaskStage.VIDEO_GENERATING);
        }
        saveContext(context);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskDetailResponse getTaskDetail(String taskId) {
        VideoTaskEntity task = taskRepository.findByTaskId(taskId).orElseThrow();
        WorkflowContext context = loadContext(taskId);
        return toDetail(task, context);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskSummary> listTasks() {
        return taskRepository.findTop20ByOrderByUpdatedAtDesc().stream()
                .map(task -> new TaskSummary(
                        task.getTaskId(),
                        task.getStatus(),
                        task.getStage(),
                        workflowType(requestFromTask(task)),
                        task.getInputText(),
                        task.getCreatedAt(),
                        task.getUpdatedAt()
                ))
                .toList();
    }

    private VideoConfig generateVideoConfig(CreateVideoTaskRequest request) {
        GenerateRequest generateRequest = toGenerateRequest(request);
        MarketInsight insight = marketAgent.analyze(generateRequest);
        String productName = valueOrDefault(insight.productName(), valueOrDefault(generateRequest.productName(), "广告商品"));
        ProductInfo productInfo = new ProductInfo(
                productName,
                valueOrDefault(generateRequest.sellingPoints(), String.join("、", insight.valuePropositions())),
                mergeReferenceResources(generateRequest.referenceImageUrls(), generateRequest.referenceImageFileIds()),
                null,
                null,
                Map.of("keywords", insight.keywords(), "creativeStrategy", insight.creativeStrategy())
        );
        return new VideoConfig(
                valueOrDefault(insight.videoType(), "商品展示视频"),
                productInfo,
                insight.targetAudience(),
                valueOrDefault(request.platform(), "抖音"),
                request.durationValue(),
                request.aspectRatioValue(),
                insight.creativeStrategy()
        );
    }

    private VideoConfig generateVideoConfigForVideoStoryboard(
            CreateVideoTaskRequest request,
            VideoStoryboardAgent.StoryboardSummary summary
    ) {
        ProductInfo productInfo = new ProductInfo(
                valueOrDefault(summary.title(), fallbackVideoStoryboardName(request)),
                valueOrDefault(request.text(), "基于视频素材总结分镜并重构广告"),
                safeImageUrls(request.imageUrls()),
                null,
                null,
                Map.of(
                        "sourceVideoUrl", valueOrDefault(request.sourceVideoUrl(), ""),
                        "sourceVideoFileId", valueOrDefault(request.sourceVideoFileId(), ""),
                        "workflowType", workflowType(request)
                )
        );
        return new VideoConfig(
                valueOrDefault(request.videoType(), "视频素材重制广告"),
                productInfo,
                "",
                valueOrDefault(request.platform(), "抖音"),
                request.durationValue(),
                request.aspectRatioValue(),
                "基于用户提供的视频素材总结分镜脚本，再生成广告重制版本。"
        );
    }

    private List<Shot> generateShots(CreateVideoTaskRequest request, VideoConfig config) {
        MarketInsight insight = new MarketInsight(
                config.videoType(),
                config.productInfo().name(),
                config.targetAudience(),
                List.of(config.productInfo().sellingPoint()),
                List.of(config.productInfo().name()),
                config.videoAdvice()
        );
        DirectorPlan plan = directorAgent.createPlan(toGenerateRequest(request), insight, config.platform());
        List<Shot> shots = plan.scenes().stream()
                .map(scene -> new Shot(
                        "shot_%03d".formatted(scene.index()),
                        scene.index(),
                        scene.seconds(),
                        scene.visualPrompt(),
                        scene.narration(),
                        scene.caption(),
                        first(config.productInfo().resources()),
                        "",
                        "product_showcase"
                ))
                .toList();
        return applyVoiceoverPolicy(request, normalizeShotDurations(shots, config.duration()));
    }

    private List<Shot> applyVoiceoverPolicy(CreateVideoTaskRequest request, List<Shot> shots) {
        if (!request.voiceoverDisabledValue()) {
            return shots;
        }
        return clearShotWords(shots);
    }

    private List<Shot> clearShotWords(List<Shot> shots) {
        return shots.stream()
                .map(shot -> new Shot(
                        shot.shotId(),
                        shot.orderNo(),
                        shot.duration(),
                        shot.prompt(),
                        shot.action(),
                        "",
                        shot.reference(),
                        shot.camera(),
                        shot.sceneType()
                ))
                .toList();
    }

    private List<Shot> normalizeShotDurations(List<Shot> shots, Integer totalDuration) {
        if (isEmpty(shots)) {
            return List.of();
        }
        int expectedTotal = positiveOrDefault(totalDuration, shots.size() * 5);
        List<Integer> sourceDurations = shots.stream()
                .map(shot -> positiveOrDefault(shot.duration(), 5))
                .toList();
        int sourceTotal = sourceDurations.stream().mapToInt(Integer::intValue).sum();
        List<Integer> normalized = new ArrayList<>();
        int assigned = 0;
        for (int index = 0; index < shots.size(); index++) {
            double exact = sourceDurations.get(index) * (double) expectedTotal / sourceTotal;
            int seconds = expectedTotal >= shots.size()
                    ? Math.max(1, (int) Math.floor(exact))
                    : (index < expectedTotal ? 1 : 0);
            normalized.add(seconds);
            assigned += seconds;
        }
        int delta = expectedTotal - assigned;
        int cursor = 0;
        while (delta != 0 && !normalized.isEmpty()) {
            int index = cursor % normalized.size();
            int current = normalized.get(index);
            if (delta > 0) {
                normalized.set(index, current + 1);
                delta -= 1;
            } else if (current > 1) {
                normalized.set(index, current - 1);
                delta += 1;
            }
            cursor += 1;
            if (cursor > normalized.size() * Math.max(1, expectedTotal + assigned)) {
                break;
            }
        }
        int finalTotal = normalized.stream().mapToInt(Integer::intValue).sum();
        log.info("Normalize shot durations, shotCount={}, expectedTotal={}, sourceTotal={}, finalTotal={}",
                shots.size(), expectedTotal, sourceTotal, finalTotal);
        List<Shot> result = new ArrayList<>();
        for (int index = 0; index < shots.size(); index++) {
            Shot shot = shots.get(index);
            result.add(new Shot(
                    shot.shotId(),
                    shot.orderNo(),
                    normalized.get(index),
                    shot.prompt(),
                    shot.action(),
                    shot.words(),
                    shot.reference(),
                    shot.camera(),
                    shot.sceneType()
            ));
        }
        return result;
    }

    private List<ShotImageGroup> generateImages(CreateVideoTaskRequest request, VideoConfig config, List<Shot> shots) {
        if (isVideoStoryboardWorkflow(workflowType(request))) {
            return generateImagesPerShot(request, config, shots);
        }
        int imageCountPerShot = request.imageCount();
        int totalImageCount = shots.size() * imageCountPerShot;
        log.info("Generate image groups with one Seedream request, shotCount={}, imageCountPerShot={}, totalImageCount={}",
                shots.size(), imageCountPerShot, totalImageCount);
        String prompt = buildBatchImagePrompt(config, shots, imageCountPerShot, request.voiceoverDisabledValue());
        List<String> urls = imageClient.generateImages(prompt, config.productInfo().resources(), totalImageCount);
        List<ShotImageGroup> groups = new ArrayList<>();
        int cursor = 0;
        for (Shot shot : shots) {
            List<ImageCandidate> images = new ArrayList<>();
            for (int index = 1; index <= imageCountPerShot; index++) {
                String url = cursor < urls.size()
                        ? urls.get(cursor)
                        : "mock://seedream/images/" + Math.abs((shot.prompt() + index).hashCode()) + ".png";
                images.add(new ImageCandidate(
                        assetId("img", shot.shotId(), index),
                        shot.shotId(),
                        index,
                        url,
                        null,
                        null,
                        false
                ));
                cursor += 1;
            }
            groups.add(new ShotImageGroup(shot.shotId(), shot.duration(), shot.prompt(), shot.action(), shot.words(), shot.reference(), images));
        }
        return groups;
    }

    private List<ShotImageGroup> generateImagesPerShot(CreateVideoTaskRequest request, VideoConfig config, List<Shot> shots) {
        int imageCountPerShot = request.imageCount();
        log.info("Generate image groups per shot, shotCount={}, imageCountPerShot={}", shots.size(), imageCountPerShot);
        return awaitAll(shots.stream()
                .map(shot -> CompletableFuture.supplyAsync(() -> generateImageGroupForShot(request, config, shot, imageCountPerShot)))
                .toList());
    }

    private ShotImageGroup generateImageGroupForShot(
            CreateVideoTaskRequest request,
            VideoConfig config,
            Shot shot,
            int imageCountPerShot
    ) {
        List<String> references = shotReferenceUrls(shot);
        List<String> urls = imageClient.generateImages(
                buildSingleShotImagePrompt(config, shot, imageCountPerShot, request.voiceoverDisabledValue()),
                references,
                imageCountPerShot
        );
        List<ImageCandidate> images = new ArrayList<>();
        for (int index = 1; index <= imageCountPerShot; index++) {
            String url = index - 1 < urls.size()
                    ? urls.get(index - 1)
                    : "mock://seedream/images/" + Math.abs((shot.shotId() + shot.prompt() + index).hashCode()) + ".png";
            images.add(new ImageCandidate(
                    assetId("img", shot.shotId(), index),
                    shot.shotId(),
                    index,
                    url,
                    null,
                    null,
                    false
            ));
        }
        return new ShotImageGroup(shot.shotId(), shot.duration(), shot.prompt(), shot.action(), shot.words(), shot.reference(), images);
    }

    private String buildSingleShotImagePrompt(VideoConfig config, Shot shot, int imageCountPerShot, boolean voiceoverDisabled) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请仅围绕分镜 ").append(shot.shotId()).append(" 生成 ").append(imageCountPerShot).append(" 张候选广告图片。");
        prompt.append("这").append(imageCountPerShot).append("张图必须全部属于同一个分镜，严禁混入其他分镜的场景、动作、结果或商品状态。");
        prompt.append("同一分镜内保持主体、场景阶段、营销意图和关键动作一致，只允许构图、角度、景别、光线和细节变化。");
        prompt.append("如果提供了参考图，必须将参考图视为当前分镜唯一参考，不得扩散到其他分镜内容。");
        prompt.append("画面比例：").append(config.aspectRatio()).append("。要求：无水印，广告可用，主体清晰。\n");
        prompt.append("分镜ID：").append(shot.shotId()).append("\n");
        prompt.append("画面总结：").append(shot.prompt()).append("\n");
        prompt.append("镜头动作：").append(shot.action()).append("\n");
        appendVoiceoverPromptLine(prompt, shot.words(), voiceoverDisabled);
        return prompt.toString();
    }

    private List<String> shotReferenceUrls(Shot shot) {
        if (shot == null || !StringUtils.hasText(shot.reference())) {
            return List.of();
        }
        String reference = shot.reference().trim();
        if (reference.startsWith("data:image/")) {
            return List.of(reference);
        }
        return java.util.Arrays.stream(reference.split("[\\n\\r,]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String buildBatchImagePrompt(VideoConfig config, List<Shot> shots, int imageCountPerShot, boolean voiceoverDisabled) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请一次性生成").append(shots.size() * imageCountPerShot).append("张广告候选图片。");
        prompt.append("必须严格按照以下分镜 ID 顺序返回结果，不允许调整顺序。");
        prompt.append("返回顺序必须是：");
        for (int shotIndex = 0; shotIndex < shots.size(); shotIndex++) {
            Shot shot = shots.get(shotIndex);
            for (int candidateIndex = 1; candidateIndex <= imageCountPerShot; candidateIndex++) {
                if (shotIndex > 0 || candidateIndex > 1) {
                    prompt.append(" -> ");
                }
                prompt.append(shot.shotId()).append("#").append(candidateIndex);
            }
        }
        prompt.append("。");
        prompt.append("同一分镜内必须连续生成").append(imageCountPerShot).append("张候选图，严禁把别的分镜图片插入当前分镜组。");
        prompt.append("同一分镜内保持商品主体、场景阶段、营销目的和动作前提一致，只允许构图、镜头角度、光线、景别和细节变化。");
        prompt.append("不得提前展示后续分镜的场景、动作、结果画面，也不得复用前一分镜的核心画面。");
        prompt.append("如果某张图更像别的分镜，必须重画为当前分镜内容，而不是跨分镜串场。");
        prompt.append("每张图都要与所属分镜 ID 完整对应，确保后端可以按返回顺序直接切分到对应分镜。");
        prompt.append("画面比例：").append(config.aspectRatio()).append("。要求：无水印，商品清晰，广告可用。\n");
        for (Shot shot : shots) {
            prompt.append("\n分镜 ").append(shot.shotId()).append("：\n");
            prompt.append("视觉提示词：").append(shot.prompt()).append("\n");
            prompt.append("动作/镜头：").append(shot.action()).append("\n");
            appendVoiceoverPromptLine(prompt, shot.words(), voiceoverDisabled);
            prompt.append("当前分镜必须连续输出 ").append(imageCountPerShot).append(" 张，仅对应 ").append(shot.shotId()).append("。\n");
        }
        return prompt.toString();
    }

    private List<ShotImageGroup> evaluateImages(List<ShotImageGroup> imageGroups) {
        log.info("Evaluate image groups concurrently, groupCount={}", imageGroups.size());
        return awaitAll(imageGroups.stream()
                .map(group -> CompletableFuture.supplyAsync(() -> scoreImageGroup(group)))
                .toList());
    }

    private ShotImageGroup scoreImageGroup(ShotImageGroup group) {
        return new ShotImageGroup(
                group.shotId(),
                group.duration(),
                group.prompt(),
                group.action(),
                group.words(),
                group.reference(),
                awaitAll(group.images().stream()
                        .map(image -> CompletableFuture.supplyAsync(() -> scoreImageCandidate(group, image)))
                        .toList())
        );
    }

    private ImageCandidate scoreImageCandidate(ShotImageGroup group, ImageCandidate image) {
        EvaluationScore evaluation = evaluateAsset(
                "image",
                group.shotId(),
                group.prompt(),
                group.action(),
                group.words(),
                image.assetId(),
                image.url(),
                score(80, image.id()),
                "LLM 评分兜底：画面与分镜描述匹配，适合作为候选素材。"
        );
        return new ImageCandidate(
                image.assetId(),
                image.shotId(),
                image.id(),
                image.url(),
                evaluation.score(),
                evaluation.reason(),
                false
        );
    }

    private List<SelectedImage> pickBestImages(List<ShotImageGroup> scoredImageGroups) {
        return scoredImageGroups.stream()
                .map(group -> {
                    ImageCandidate best = group.images().stream()
                            .max(Comparator.comparing(image -> valueOrZero(image.score())))
                            .orElseThrow();
                    ImageCandidate selected = new ImageCandidate(best.assetId(), best.shotId(), best.id(), best.url(), best.score(), best.reason(), true);
                    return new SelectedImage(group.shotId(), group.duration(), selected, group.prompt(), group.action(), group.words());
                })
                .toList();
    }

    private List<SelectedImage> pickSelectedOrBestImages(List<ShotImageGroup> scoredImageGroups) {
        return scoredImageGroups.stream()
                .map(group -> {
                    ImageCandidate chosen = group.images().stream()
                            .filter(image -> Boolean.TRUE.equals(image.selected()))
                            .findFirst()
                            .orElseGet(() -> group.images().stream()
                                    .max(Comparator.comparing(image -> valueOrZero(image.score())))
                                    .orElseThrow());
                    ImageCandidate selected = new ImageCandidate(chosen.assetId(), chosen.shotId(), chosen.id(), chosen.url(), chosen.score(), chosen.reason(), true);
                    return new SelectedImage(group.shotId(), group.duration(), selected, group.prompt(), group.action(), group.words());
                })
                .toList();
    }

    private List<SelectedImage> pickFirstImages(List<ShotImageGroup> imageGroups) {
        return imageGroups.stream()
                .filter(group -> !isEmpty(group.images()))
                .map(group -> {
                    ImageCandidate first = group.images().get(0);
                    ImageCandidate selected = new ImageCandidate(first.assetId(), first.shotId(), first.id(), first.url(), first.score(), first.reason(), true);
                    return new SelectedImage(group.shotId(), group.duration(), selected, group.prompt(), group.action(), group.words());
                })
                .toList();
    }

    private List<ShotVideoGroup> generateVideos(CreateVideoTaskRequest request, VideoConfig config, List<SelectedImage> selectedImages) {
        log.info("Generate video groups concurrently, selectedImageCount={}, videoCountPerShot={}", selectedImages.size(), request.videoCount());
        return awaitAll(selectedImages.stream()
                .map(selectedImage -> CompletableFuture.supplyAsync(() -> generateVideoGroup(request, config, selectedImage)))
                .toList());
    }

    private ShotVideoGroup generateVideoGroup(CreateVideoTaskRequest request, VideoConfig config, SelectedImage selectedImage) {
        List<VideoCandidate> videos = awaitAll(java.util.stream.IntStream.rangeClosed(1, request.videoCount())
                .mapToObj(index -> CompletableFuture.supplyAsync(() -> generateVideoCandidate(request, config, selectedImage, index)))
                .toList());
        return new ShotVideoGroup(
                selectedImage.shotId(),
                selectedImage.duration(),
                selectedImage.prompt(),
                selectedImage.action(),
                selectedImage.words(),
                selectedImage.image().url(),
                videos
        );
    }

    private VideoCandidate generateVideoCandidate(
            CreateVideoTaskRequest request,
            VideoConfig config,
            SelectedImage selectedImage,
            int index
    ) {
        int durationSeconds = positiveOrDefault(selectedImage.duration(), config.duration());
        StringBuilder prompt = new StringBuilder()
                .append(selectedImage.prompt())
                .append("\n动作：").append(selectedImage.action());
        if (request.voiceoverDisabledValue()) {
            prompt.append("\n本任务禁用口播/旁白，视频中不出现口播、旁白或字幕。");
        } else if (StringUtils.hasText(selectedImage.words())) {
            prompt.append("\n口播/旁白：").append(selectedImage.words());
        }
        prompt.append("\n时长：").append(durationSeconds).append("秒")
                .append("\n无水印，比例").append(config.aspectRatio());
        SeedanceVideoClient.VideoGeneration generation = videoClient.generateVideo(
                config.productInfo().name(),
                List.of(selectedImage.image().url()),
                prompt.toString(),
                durationSeconds,
                config.aspectRatio()
        );
        return new VideoCandidate(
                assetId("vid", selectedImage.shotId(), index),
                selectedImage.shotId(),
                index,
                generation.videoUrl(),
                null,
                null,
                false
        );
    }

    private List<ShotVideoGroup> evaluateVideos(List<ShotVideoGroup> videoGroups) {
        log.info("Evaluate video groups concurrently, groupCount={}", videoGroups.size());
        return awaitAll(videoGroups.stream()
                .map(group -> CompletableFuture.supplyAsync(() -> scoreVideoGroup(group)))
                .toList());
    }

    private ShotVideoGroup scoreVideoGroup(ShotVideoGroup group) {
        return new ShotVideoGroup(
                group.shotId(),
                group.duration(),
                group.prompt(),
                group.action(),
                group.words(),
                group.reference(),
                awaitAll(group.videos().stream()
                        .map(video -> CompletableFuture.supplyAsync(() -> scoreVideoCandidate(group, video)))
                        .toList())
        );
    }

    private VideoCandidate scoreVideoCandidate(ShotVideoGroup group, VideoCandidate video) {
        EvaluationScore evaluation = evaluateAsset(
                "video",
                group.shotId(),
                group.prompt(),
                group.action(),
                group.words(),
                video.assetId(),
                video.url(),
                score(85, video.id()),
                "LLM 评分兜底：主体稳定，镜头动作符合分镜，可用于最终合成。"
        );
        return new VideoCandidate(
                video.assetId(),
                video.shotId(),
                video.id(),
                video.url(),
                evaluation.score(),
                evaluation.reason(),
                false
        );
    }

    private EvaluationScore evaluateAsset(
            String assetType,
            String shotId,
            String prompt,
            String action,
            String words,
            String assetId,
            String assetUrl,
            BigDecimal fallbackScore,
            String fallbackReason
    ) {
        String response = chatClient.complete(
                promptService.evaluateAgent(),
                """
                        请评估以下%s候选素材，并严格按照系统提示词中的当前输出格式返回 JSON。
                        分镜 ID：%s
                        视觉提示词：%s
                        动作/镜头：%s
                        口播/旁白：%s
                        素材 ID：%s
                        素材 URL：%s
                        """.formatted(
                        assetTypeLabel(assetType),
                        shotId,
                        prompt,
                        action,
                        voiceoverDisabledValue(words),
                        assetId,
                        summarizeAssetUrl(assetUrl)
                ),
                "image".equals(assetType) && StringUtils.hasText(assetUrl) ? List.of(assetUrl) : List.of()
        );
        return parseEvaluation(response, fallbackScore, fallbackReason);
    }

    private EvaluationScore parseEvaluation(String response, BigDecimal fallbackScore, String fallbackReason) {
        for (String candidate : jsonCandidates(response)) {
            try {
                JsonNode root = objectMapper.readTree(candidate);
                BigDecimal parsedScore = BigDecimal.valueOf(Math.max(0, Math.min(100, root.path("score").asInt(fallbackScore.intValue()))));
                String reason = root.path("reason").asText(fallbackReason);
                return new EvaluationScore(parsedScore, valueOrDefault(reason, fallbackReason));
            } catch (Exception ignored) {
                // Try the next possible JSON fragment.
            }
        }
        return new EvaluationScore(fallbackScore, fallbackReason + " 模型输出未能解析为评分 JSON。");
    }

    private List<String> jsonCandidates(String response) {
        List<String> candidates = new ArrayList<>();
        if (!StringUtils.hasText(response)) {
            return candidates;
        }
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

    private String assetTypeLabel(String assetType) {
        return "video".equals(assetType) ? "视频" : "图片";
    }

    private void appendVoiceoverPromptLine(StringBuilder prompt, String words, boolean voiceoverDisabled) {
        if (voiceoverDisabled) {
            prompt.append("口播/旁白：无（本任务禁用口播/旁白，画面中不出现字幕或口播文案）\n");
            return;
        }
        prompt.append("口播/旁白：").append(words).append("\n");
    }

    private String voiceoverDisabledValue(String words) {
        return StringUtils.hasText(words) ? words : "无";
    }

    private String summarizeAssetUrl(String assetUrl) {
        if (!StringUtils.hasText(assetUrl)) {
            return "未提供";
        }
        if (assetUrl.startsWith("data:")) {
            return assetUrl.substring(0, Math.min(assetUrl.length(), 80)) + "...(base64 omitted)";
        }
        return assetUrl;
    }

    private List<SelectedVideo> pickBestVideos(List<ShotVideoGroup> scoredVideoGroups) {
        return scoredVideoGroups.stream()
                .map(group -> {
                    VideoCandidate best = group.videos().stream()
                            .max(Comparator.comparing(video -> valueOrZero(video.score())))
                            .orElseThrow();
                    VideoCandidate selected = new VideoCandidate(best.assetId(), best.shotId(), best.id(), best.url(), best.score(), best.reason(), true);
                    return new SelectedVideo(group.shotId(), group.duration(), selected, group.words(), group.action());
                })
                .toList();
    }

    private List<SelectedVideo> pickSelectedOrBestVideos(List<ShotVideoGroup> scoredVideoGroups) {
        return scoredVideoGroups.stream()
                .map(group -> {
                    VideoCandidate chosen = group.videos().stream()
                            .filter(video -> Boolean.TRUE.equals(video.selected()))
                            .findFirst()
                            .orElseGet(() -> group.videos().stream()
                                    .max(Comparator.comparing(video -> valueOrZero(video.score())))
                                    .orElseThrow());
                    VideoCandidate selected = new VideoCandidate(chosen.assetId(), chosen.shotId(), chosen.id(), chosen.url(), chosen.score(), chosen.reason(), true);
                    return new SelectedVideo(group.shotId(), group.duration(), selected, group.words(), group.action());
                })
                .toList();
    }

    private List<SelectedVideo> pickFirstVideos(List<ShotVideoGroup> videoGroups) {
        return videoGroups.stream()
                .filter(group -> !isEmpty(group.videos()))
                .map(group -> {
                    VideoCandidate first = group.videos().get(0);
                    VideoCandidate selected = new VideoCandidate(first.assetId(), first.shotId(), first.id(), first.url(), first.score(), first.reason(), true);
                    return new SelectedVideo(group.shotId(), group.duration(), selected, group.words(), group.action());
                })
                .toList();
    }

    private FinalVideo composeFinalVideo(String taskId, CreateVideoTaskRequest request, VideoConfig config, List<SelectedVideo> selectedVideos) {
        List<SelectedVideo> safeSelectedVideos = selectedVideos == null ? List.of() : selectedVideos;
        String finalVideoUrl = ffmpegComposeService.compose(
                taskId,
                safeSelectedVideos.stream()
                        .map(selectedVideo -> selectedVideo.video().url())
                        .toList()
        );
        ReleasePlan releasePlan = createReleasePlanOrFallback(taskId, request, config, finalVideoUrl);
        return new FinalVideo(
                finalVideoUrl,
                releasePlan.headline(),
                releasePlan.description(),
                releasePlan.hashtags(),
                safeSelectedVideos
        );
    }

    private ReleasePlan createReleasePlanOrFallback(String taskId, CreateVideoTaskRequest request, VideoConfig config, String finalVideoUrl) {
        try {
            return releaseAgent.createReleasePlan(
                    toGenerateRequest(request),
                    new MultimediaResult(finalVideoUrl, "", List.of(), List.of()),
                    config
            );
        } catch (RuntimeException ex) {
            log.error("Release plan failed after final video composed, taskId={}, finalVideoUrl={}", taskId, finalVideoUrl, ex);
            String productName = fallbackProductName(request, config);
            return new ReleasePlan(
                    productName + "广告视频",
                    "视频已合成，可直接预览和下载。",
                    List.of("#" + productName, "#广告视频"),
                    finalVideoUrl
            );
        }
    }

    private String fallbackProductName(CreateVideoTaskRequest request, VideoConfig config) {
        if (config != null
                && config.productInfo() != null
                && StringUtils.hasText(config.productInfo().name())) {
            return config.productInfo().name();
        }
        if (StringUtils.hasText(request.text())) {
            return "商品";
        }
        return "广告商品";
    }

    @Transactional
    protected void markStage(String taskId, TaskStage stage) {
        VideoTaskEntity task = taskRepository.findByTaskId(taskId).orElseThrow();
        task.setStatus(TaskStatus.RUNNING.name());
        task.setStage(stage.name());
        task.setProgress(stage.progress());
        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);
        log.info("Video task stage update, taskId={}, stage={}, progress={}", taskId, stage, stage.progress());
    }

    @Transactional
    protected void markWaitingReview(String taskId, TaskStage stage) {
        VideoTaskEntity task = taskRepository.findByTaskId(taskId).orElseThrow();
        task.setStatus(TaskStatus.WAITING_REVIEW.name());
        task.setStage(stage.name());
        task.setProgress(stage.progress());
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);
        log.info("Video task waiting review, taskId={}, stage={}", taskId, stage);
    }

    @Transactional
    protected void markSuccess(String taskId, String videoUrl) {
        VideoTaskEntity task = taskRepository.findByTaskId(taskId).orElseThrow();
        task.setStatus(TaskStatus.SUCCESS.name());
        task.setStage(TaskStage.COMPLETED.name());
        task.setProgress(TaskStage.COMPLETED.progress());
        task.setFinalVideoUrl(videoUrl);
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);
    }

    @Transactional
    protected void markFailed(String taskId, RuntimeException ex) {
        VideoTaskEntity task = taskRepository.findByTaskId(taskId).orElseThrow();
        task.setStatus(TaskStatus.FAILED.name());
        task.setStage(TaskStage.FAILED.name());
        task.setErrorCode("WORKFLOW_FAILED");
        task.setErrorMessage(ex.getMessage());
        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);
    }

    private void clearFromStage(String taskId, TaskStage stage) {
        WorkflowContext context = loadContext(taskId);
        if (stage.ordinal() <= TaskStage.MARKET_PLANNING.ordinal()) {
            context.setVideoConfig(null);
        }
        if (stage.ordinal() <= TaskStage.SHOT_SCRIPT_GENERATING.ordinal()) {
            context.setShots(List.of());
        }
        if (stage.ordinal() <= TaskStage.IMAGE_GENERATING.ordinal()) {
            context.setImageGroups(List.of());
            context.setScoredImageGroups(List.of());
            context.setSelectedImages(List.of());
        }
        if (stage.ordinal() <= TaskStage.VIDEO_GENERATING.ordinal()) {
            context.setVideoGroups(List.of());
            context.setScoredVideoGroups(List.of());
            context.setSelectedVideos(List.of());
        }
        if (stage.ordinal() <= TaskStage.FINAL_COMPOSING.ordinal()) {
            context.setFinalVideo(null);
        }
        saveContext(context);
        markStage(taskId, stage);
    }

    private List<SelectedImage> selectImages(List<ShotImageGroup> groups, List<SelectAssetsRequest.AssetSelection> selections) {
        Map<String, String> selectedAssetByShot = selections.stream()
                .collect(java.util.stream.Collectors.toMap(
                        SelectAssetsRequest.AssetSelection::shotId,
                        SelectAssetsRequest.AssetSelection::assetId,
                        (first, ignored) -> first
                ));
        return groups.stream()
                .flatMap(group -> group.images().stream()
                        .filter(image -> image.assetId().equals(selectedAssetByShot.get(group.shotId())))
                        .map(image -> new SelectedImage(group.shotId(), group.duration(), image, group.prompt(), group.action(), group.words())))
                .toList();
    }

    private List<SelectedVideo> selectVideos(List<ShotVideoGroup> groups, List<SelectAssetsRequest.AssetSelection> selections) {
        Map<String, String> selectedAssetByShot = selections.stream()
                .collect(java.util.stream.Collectors.toMap(
                        SelectAssetsRequest.AssetSelection::shotId,
                        SelectAssetsRequest.AssetSelection::assetId,
                        (first, ignored) -> first
                ));
        return groups.stream()
                .flatMap(group -> group.videos().stream()
                        .filter(video -> video.assetId().equals(selectedAssetByShot.get(group.shotId())))
                        .map(video -> new SelectedVideo(group.shotId(), group.duration(), video, group.words(), group.action())))
                .toList();
    }

    /**
     * 功能描述：按照图片分镜组顺序重新排列已选图片，避免前端提交顺序影响后续视频生成顺序。
     * 参数解释：groups 表示当前图片分镜组；selectedImages 表示用户已选择的图片列表。
     * 返回对象描述：返回按分镜组顺序排列的已选图片列表。
     * 可能抛出的异常：无。
     */
    private List<SelectedImage> orderSelectedImages(List<ShotImageGroup> groups, List<SelectedImage> selectedImages) {
        if (isEmpty(groups) || isEmpty(selectedImages)) {
            return selectedImages == null ? List.of() : selectedImages;
        }
        Map<String, SelectedImage> selectedByShot = selectedImages.stream()
                .collect(java.util.stream.Collectors.toMap(
                        SelectedImage::shotId,
                        selected -> selected,
                        (first, ignored) -> first
                ));
        return groups.stream()
                .map(group -> selectedByShot.get(group.shotId()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * 功能描述：按照视频分镜组顺序重新排列已选视频，避免前端提交顺序影响最终合成顺序。
     * 参数解释：groups 表示当前视频分镜组；selectedVideos 表示用户已选择的视频列表。
     * 返回对象描述：返回按分镜组顺序排列的已选视频列表。
     * 可能抛出的异常：无。
     */
    private List<SelectedVideo> orderSelectedVideos(List<ShotVideoGroup> groups, List<SelectedVideo> selectedVideos) {
        if (isEmpty(groups) || isEmpty(selectedVideos)) {
            return selectedVideos == null ? List.of() : selectedVideos;
        }
        Map<String, SelectedVideo> selectedByShot = selectedVideos.stream()
                .collect(java.util.stream.Collectors.toMap(
                        SelectedVideo::shotId,
                        selected -> selected,
                        (first, ignored) -> first
                ));
        return groups.stream()
                .map(group -> selectedByShot.get(group.shotId()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private List<Shot> mergeEditableShotFields(List<Shot> currentShots, List<Shot> editedShots) {
        if (isEmpty(currentShots)) {
            return editedShots;
        }
        return currentShots.stream()
                .map(current -> editedShots.stream()
                        .filter(edited -> current.shotId().equals(edited.shotId()))
                        .findFirst()
                        .map(edited -> new Shot(
                                current.shotId(),
                                current.orderNo(),
                                edited.duration() != null ? edited.duration() : current.duration(),
                                edited.prompt() != null ? edited.prompt() : current.prompt(),
                                edited.action() != null ? edited.action() : current.action(),
                                edited.words() != null ? edited.words() : current.words(),
                                StringUtils.hasText(edited.reference()) ? edited.reference() : current.reference(),
                                current.camera(),
                                current.sceneType()
                        ))
                        .orElse(current))
                .toList();
    }

    private WorkflowContext loadContext(String taskId) {
        VideoTaskContextEntity entity = contextRepository.findByTaskId(taskId).orElseThrow();
        try {
            return objectMapper.readValue(entity.getContextJson(), WorkflowContext.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Workflow context deserialization failed", ex);
        }
    }

    @Transactional
    protected void saveContext(WorkflowContext context) {
        VideoTaskContextEntity entity = contextRepository.findByTaskId(context.getTaskId()).orElseThrow();
        entity.setContextJson(toJson(context));
        entity.setUpdatedAt(Instant.now());
        contextRepository.save(entity);
    }

    private TaskDetailResponse toDetail(VideoTaskEntity task, WorkflowContext context) {
        return new TaskDetailResponse(
                task.getTaskId(),
                task.getStatus(),
                task.getStage(),
                task.getProgress(),
                workflowType(requestFromTask(task)),
                requestFromTask(task),
                context.getVideoConfig(),
                context.getShots(),
                context.getImageGroups(),
                context.getScoredImageGroups(),
                context.getSelectedImages(),
                context.getVideoGroups(),
                context.getScoredVideoGroups(),
                context.getSelectedVideos(),
                context.getFinalVideo(),
                task.getErrorCode(),
                task.getErrorMessage(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private CreateVideoTaskRequest requestFromTask(VideoTaskEntity task) {
        if (StringUtils.hasText(task.getRequestJson())) {
            try {
                return objectMapper.readValue(task.getRequestJson(), CreateVideoTaskRequest.class);
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Video task request deserialization failed, taskId=" + task.getTaskId(), ex);
            }
        }
        return new CreateVideoTaskRequest(
                WORKFLOW_PRODUCT_IMAGE_AD,
                task.getInputType(),
                task.getInputText(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                task.getVideoType(),
                task.getPlatform(),
                task.getDuration(),
                task.getAspectRatio(),
                task.getStyle(),
                false,
                false,
                false,
                false,
                2,
                1
        );
    }

    private boolean shouldPauseForReview(CreateVideoTaskRequest request, TaskStage stage, boolean manualSelectionRequired) {
        if (manualSelectionRequired) {
            return true;
        }
        return !request.autoConfirmEnabledValue();
    }

    private GenerateRequest toGenerateRequest(CreateVideoTaskRequest request) {
        String prompt = valueOrDefault(request.text(), defaultPrompt());
        MediaResourceUtils.SplitImageResources resources = MediaResourceUtils.splitImageResources(
                request.imageUrls(),
                request.imageFileIds()
        );
        return new GenerateRequest(
                prompt,
                null,
                prompt,
                null,
                null,
                request.style(),
                String.valueOf(request.durationValue()),
                request.voiceoverDisabled(),
                resources.imageUrls(),
                resources.legacyFileIds()
        );
    }

    private List<String> safeImageUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return List.of();
        }
        return imageUrls.stream()
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<String> safeImageFileIds(List<String> imageFileIds) {
        if (imageFileIds == null || imageFileIds.isEmpty()) {
            return List.of();
        }
        return imageFileIds.stream()
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<String> mergeReferenceResources(List<String> imageUrls, List<String> imageFileIds) {
        return MediaResourceUtils.mergeImageResources(imageUrls, imageFileIds);
    }

    private String defaultPrompt() {
        return "参考上传的商品图片，生成一条带货广告视频。";
    }

    private String workflowType(CreateVideoTaskRequest request) {
        return request == null ? WORKFLOW_PRODUCT_IMAGE_AD : request.workflowTypeValue();
    }

    private boolean isVideoStoryboardWorkflow(String workflowType) {
        return WORKFLOW_VIDEO_STORYBOARD_AD.equals(workflowType);
    }

    private String fallbackVideoStoryboardName(CreateVideoTaskRequest request) {
        if (StringUtils.hasText(request.sourceVideoFileName())) {
            return request.sourceVideoFileName();
        }
        if (StringUtils.hasText(request.sourceVideoUrl())) {
            return request.sourceVideoUrl();
        }
        return "视频素材分镜";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON serialization failed", ex);
        }
    }

    private BigDecimal score(int base, Integer id) {
        return BigDecimal.valueOf(Math.min(99, base + (id == null ? 0 : id)));
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int positiveOrDefault(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String assetId(String prefix, String shotId, int index) {
        return prefix + "_" + shotId + "_" + index;
    }

    private String valueOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String first(List<String> values) {
        return values == null || values.isEmpty() ? "" : values.get(0);
    }

    private <T> List<T> awaitAll(List<CompletableFuture<T>> futures) {
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }

    private void advanceAfterCommit(String taskId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            advanceAsync(taskId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                advanceAsync(taskId);
            }
        });
    }

    private record EvaluationScore(BigDecimal score, String reason) {
    }
}
