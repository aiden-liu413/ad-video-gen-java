package com.volcengine.demo.advideo.dto;

import com.volcengine.demo.advideo.dto.GenerationResult.DirectorPlan;
import com.volcengine.demo.advideo.dto.GenerationResult.Evaluation;
import com.volcengine.demo.advideo.dto.GenerationResult.MarketInsight;
import com.volcengine.demo.advideo.dto.GenerationResult.MultimediaResult;
import com.volcengine.demo.advideo.dto.GenerationResult.ReleasePlan;

import java.util.List;

public class GenerationCheckpoint {

    private String currentStep;
    private MarketInsight marketInsight;
    private DirectorPlan directorPlan;
    private Evaluation evaluation;
    private List<String> imagePrompts;
    private List<String> seedreamImageUrls;
    private MultimediaResult multimedia;
    private ReleasePlan releasePlan;

    public String getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
    }

    public MarketInsight getMarketInsight() {
        return marketInsight;
    }

    public void setMarketInsight(MarketInsight marketInsight) {
        this.marketInsight = marketInsight;
    }

    public DirectorPlan getDirectorPlan() {
        return directorPlan;
    }

    public void setDirectorPlan(DirectorPlan directorPlan) {
        this.directorPlan = directorPlan;
    }

    public Evaluation getEvaluation() {
        return evaluation;
    }

    public void setEvaluation(Evaluation evaluation) {
        this.evaluation = evaluation;
    }

    public List<String> getImagePrompts() {
        return imagePrompts;
    }

    public void setImagePrompts(List<String> imagePrompts) {
        this.imagePrompts = imagePrompts;
    }

    public List<String> getSeedreamImageUrls() {
        return seedreamImageUrls;
    }

    public void setSeedreamImageUrls(List<String> seedreamImageUrls) {
        this.seedreamImageUrls = seedreamImageUrls;
    }

    public MultimediaResult getMultimedia() {
        return multimedia;
    }

    public void setMultimedia(MultimediaResult multimedia) {
        this.multimedia = multimedia;
    }

    public ReleasePlan getReleasePlan() {
        return releasePlan;
    }

    public void setReleasePlan(ReleasePlan releasePlan) {
        this.releasePlan = releasePlan;
    }
}
