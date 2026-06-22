package com.volcengine.demo.advideo.orchestrator;

import com.volcengine.demo.advideo.domain.model.FinalVideo;
import com.volcengine.demo.advideo.domain.model.SelectedImage;
import com.volcengine.demo.advideo.domain.model.SelectedVideo;
import com.volcengine.demo.advideo.domain.model.Shot;
import com.volcengine.demo.advideo.domain.model.ShotImageGroup;
import com.volcengine.demo.advideo.domain.model.ShotVideoGroup;
import com.volcengine.demo.advideo.domain.model.VideoConfig;

import java.util.ArrayList;
import java.util.List;

public class WorkflowContext {

    private String taskId;
    private VideoConfig videoConfig;
    private String sourceStoryboardTitle;
    private List<Shot> shots = new ArrayList<>();
    private List<ShotImageGroup> imageGroups = new ArrayList<>();
    private List<ShotImageGroup> scoredImageGroups = new ArrayList<>();
    private List<SelectedImage> selectedImages = new ArrayList<>();
    private List<ShotVideoGroup> videoGroups = new ArrayList<>();
    private List<ShotVideoGroup> scoredVideoGroups = new ArrayList<>();
    private List<SelectedVideo> selectedVideos = new ArrayList<>();
    private FinalVideo finalVideo;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public VideoConfig getVideoConfig() {
        return videoConfig;
    }

    public void setVideoConfig(VideoConfig videoConfig) {
        this.videoConfig = videoConfig;
    }

    public String getSourceStoryboardTitle() {
        return sourceStoryboardTitle;
    }

    public void setSourceStoryboardTitle(String sourceStoryboardTitle) {
        this.sourceStoryboardTitle = sourceStoryboardTitle;
    }

    public List<Shot> getShots() {
        return shots;
    }

    public void setShots(List<Shot> shots) {
        this.shots = shots == null ? new ArrayList<>() : shots;
    }

    public List<ShotImageGroup> getImageGroups() {
        return imageGroups;
    }

    public void setImageGroups(List<ShotImageGroup> imageGroups) {
        this.imageGroups = imageGroups == null ? new ArrayList<>() : imageGroups;
    }

    public List<ShotImageGroup> getScoredImageGroups() {
        return scoredImageGroups;
    }

    public void setScoredImageGroups(List<ShotImageGroup> scoredImageGroups) {
        this.scoredImageGroups = scoredImageGroups == null ? new ArrayList<>() : scoredImageGroups;
    }

    public List<SelectedImage> getSelectedImages() {
        return selectedImages;
    }

    public void setSelectedImages(List<SelectedImage> selectedImages) {
        this.selectedImages = selectedImages == null ? new ArrayList<>() : selectedImages;
    }

    public List<ShotVideoGroup> getVideoGroups() {
        return videoGroups;
    }

    public void setVideoGroups(List<ShotVideoGroup> videoGroups) {
        this.videoGroups = videoGroups == null ? new ArrayList<>() : videoGroups;
    }

    public List<ShotVideoGroup> getScoredVideoGroups() {
        return scoredVideoGroups;
    }

    public void setScoredVideoGroups(List<ShotVideoGroup> scoredVideoGroups) {
        this.scoredVideoGroups = scoredVideoGroups == null ? new ArrayList<>() : scoredVideoGroups;
    }

    public List<SelectedVideo> getSelectedVideos() {
        return selectedVideos;
    }

    public void setSelectedVideos(List<SelectedVideo> selectedVideos) {
        this.selectedVideos = selectedVideos == null ? new ArrayList<>() : selectedVideos;
    }

    public FinalVideo getFinalVideo() {
        return finalVideo;
    }

    public void setFinalVideo(FinalVideo finalVideo) {
        this.finalVideo = finalVideo;
    }
}
