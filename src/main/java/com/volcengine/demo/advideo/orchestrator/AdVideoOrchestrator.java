package com.volcengine.demo.advideo.orchestrator;

import com.volcengine.demo.advideo.agent.DirectorAgent;
import com.volcengine.demo.advideo.agent.EvaluateAgent;
import com.volcengine.demo.advideo.agent.MarketAgent;
import com.volcengine.demo.advideo.agent.ReleaseAgent;
import com.volcengine.demo.advideo.dto.GenerationCheckpoint;
import com.volcengine.demo.advideo.dto.GenerateRequest;
import com.volcengine.demo.advideo.dto.GenerationResult;
import com.volcengine.demo.advideo.dto.GenerationResult.DirectorPlan;
import com.volcengine.demo.advideo.dto.GenerationResult.Evaluation;
import com.volcengine.demo.advideo.dto.GenerationResult.MarketInsight;
import com.volcengine.demo.advideo.dto.GenerationResult.MultimediaResult;
import com.volcengine.demo.advideo.dto.GenerationResult.ReleasePlan;
import com.volcengine.demo.advideo.service.MultimediaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
public class AdVideoOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AdVideoOrchestrator.class);

    private final MarketAgent marketAgent;
    private final DirectorAgent directorAgent;
    private final EvaluateAgent evaluateAgent;
    private final MultimediaService multimediaService;
    private final ReleaseAgent releaseAgent;

    public AdVideoOrchestrator(
            MarketAgent marketAgent,
            DirectorAgent directorAgent,
            EvaluateAgent evaluateAgent,
            MultimediaService multimediaService,
            ReleaseAgent releaseAgent
    ) {
        this.marketAgent = marketAgent;
        this.directorAgent = directorAgent;
        this.evaluateAgent = evaluateAgent;
        this.multimediaService = multimediaService;
        this.releaseAgent = releaseAgent;
    }

    public GenerationResult generate(String taskId, GenerateRequest request) {
        return generate(taskId, request, new GenerationCheckpoint());
    }

    public GenerationResult generate(String taskId, GenerateRequest request, GenerationCheckpoint checkpoint) {
        return generate(taskId, request, checkpoint, ignored -> {
        });
    }

    public GenerationResult generate(
            String taskId,
            GenerateRequest request,
            GenerationCheckpoint checkpoint,
            Consumer<GenerationCheckpoint> checkpointSaver
    ) {
        MarketInsight insight = checkpoint.getMarketInsight();
        if (insight == null) {
            checkpoint.setCurrentStep("MarketAgent");
            checkpointSaver.accept(checkpoint);
            log.info("[{}] Step 1/5 MarketAgent start", taskId);
            insight = marketAgent.analyze(request);
            checkpoint.setMarketInsight(insight);
            checkpointSaver.accept(checkpoint);
            log.info("[{}] Step 1/5 MarketAgent done, audience={}, keywordCount={}",
                    taskId, insight.targetAudience(), insight.keywords().size());
        } else {
            log.info("[{}] Step 1/5 MarketAgent reuse checkpoint", taskId);
        }

        DirectorPlan plan = checkpoint.getDirectorPlan();
        if (plan == null) {
            checkpoint.setCurrentStep("DirectorAgent");
            checkpointSaver.accept(checkpoint);
            log.info("[{}] Step 2/5 DirectorAgent start", taskId);
            plan = directorAgent.createPlan(request, insight);
            checkpoint.setDirectorPlan(plan);
            checkpointSaver.accept(checkpoint);
            log.info("[{}] Step 2/5 DirectorAgent done, title={}, sceneCount={}",
                    taskId, plan.title(), plan.scenes().size());
        } else {
            log.info("[{}] Step 2/5 DirectorAgent reuse checkpoint", taskId);
        }

        MultimediaResult multimedia = checkpoint.getMultimedia();
        if (multimedia == null) {
            checkpoint.setCurrentStep("MultimediaService");
            checkpointSaver.accept(checkpoint);
            log.info("[{}] Step 3/5 MultimediaService start", taskId);
            multimedia = multimediaService.generate(request, plan, checkpoint, checkpointSaver);
            checkpoint.setMultimedia(multimedia);
            checkpointSaver.accept(checkpoint);
            log.info("[{}] Step 3/5 MultimediaService done, seedreamImageCount={}, seedanceTaskId={}",
                    taskId, multimedia.seedreamImageUrls().size(), multimedia.seedanceTaskId());
        } else {
            log.info("[{}] Step 3/5 MultimediaService reuse checkpoint", taskId);
        }

        Evaluation evaluation = checkpoint.getEvaluation();
        if (evaluation == null) {
            checkpoint.setCurrentStep("EvaluateAgent");
            checkpointSaver.accept(checkpoint);
            log.info("[{}] Step 4/5 EvaluateAgent start", taskId);
            evaluation = evaluateAgent.evaluate(plan, multimedia);
            checkpoint.setEvaluation(evaluation);
            checkpointSaver.accept(checkpoint);
            log.info("[{}] Step 4/5 EvaluateAgent done, score={}", taskId, evaluation.score());
        } else {
            log.info("[{}] Step 4/5 EvaluateAgent reuse checkpoint", taskId);
        }

        ReleasePlan releasePlan = checkpoint.getReleasePlan();
        if (releasePlan == null) {
            checkpoint.setCurrentStep("ReleaseAgent");
            checkpointSaver.accept(checkpoint);
            log.info("[{}] Step 5/5 ReleaseAgent start", taskId);
            releasePlan = releaseAgent.createReleasePlan(request, multimedia);
            checkpoint.setReleasePlan(releasePlan);
            checkpointSaver.accept(checkpoint);
            log.info("[{}] Step 5/5 ReleaseAgent done, shortLink={}", taskId, releasePlan.shortLink());
        } else {
            log.info("[{}] Step 5/5 ReleaseAgent reuse checkpoint", taskId);
        }
        checkpoint.setCurrentStep("DONE");
        checkpointSaver.accept(checkpoint);
        return new GenerationResult(
                taskId,
                request.productName() == null || request.productName().isBlank() ? "广告商品" : request.productName(),
                insight,
                plan,
                evaluation,
                multimedia,
                releasePlan
        );
    }
}
