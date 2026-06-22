package com.volcengine.demo.advideo.controller;

import com.volcengine.demo.advideo.client.ArkFileClient;
import com.volcengine.demo.advideo.dto.ApiResponse;
import com.volcengine.demo.advideo.dto.CreateVideoTaskRequest;
import com.volcengine.demo.advideo.dto.CreateVideoTaskResponse;
import com.volcengine.demo.advideo.dto.RegenerateVideoTaskRequest;
import com.volcengine.demo.advideo.dto.SelectAssetsRequest;
import com.volcengine.demo.advideo.dto.TaskDetailResponse;
import com.volcengine.demo.advideo.dto.TaskSummary;
import com.volcengine.demo.advideo.dto.UploadVideoResponse;
import com.volcengine.demo.advideo.dto.UpdateWorkflowContextRequest;
import com.volcengine.demo.advideo.orchestrator.WorkflowOrchestratorService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/video-tasks")
public class VideoTaskController {

    private static final Logger log = LoggerFactory.getLogger(VideoTaskController.class);

    private final WorkflowOrchestratorService workflowOrchestratorService;
    private final ArkFileClient arkFileClient;

    public VideoTaskController(WorkflowOrchestratorService workflowOrchestratorService, ArkFileClient arkFileClient) {
        this.workflowOrchestratorService = workflowOrchestratorService;
        this.arkFileClient = arkFileClient;
    }

    @PostMapping
    public ApiResponse<CreateVideoTaskResponse> create(@Valid @RequestBody CreateVideoTaskRequest request) {
        String taskId = workflowOrchestratorService.createTask(request);
        return ApiResponse.success(new CreateVideoTaskResponse(taskId));
    }

    @PostMapping("/upload-video")
    public ApiResponse<UploadVideoResponse> uploadVideo(@RequestParam("file") MultipartFile file) {
        ArkFileClient.UploadResult uploadResult = arkFileClient.uploadVideo(file);
        return ApiResponse.success(new UploadVideoResponse(uploadResult.fileId(), uploadResult.fileName()));
    }

    @PostMapping("/upload-image")
    public ApiResponse<UploadVideoResponse> uploadImage(@RequestParam("file") MultipartFile file) {
        ArkFileClient.UploadResult uploadResult = arkFileClient.uploadImage(file);
        return ApiResponse.success(new UploadVideoResponse(uploadResult.fileId(), uploadResult.fileName()));
    }

    @PostMapping("/{taskId}/start")
    public ApiResponse<Void> start(@PathVariable String taskId) {
        log.info("Start video task, taskId={}", taskId);
        workflowOrchestratorService.startAsync(taskId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{taskId}/advance")
    public ApiResponse<Void> advance(@PathVariable String taskId) {
        log.info("Advance video task, taskId={}", taskId);
        workflowOrchestratorService.advanceAsync(taskId);
        return ApiResponse.success(null);
    }

    @GetMapping
    public ApiResponse<List<TaskSummary>> list() {
        return ApiResponse.success(workflowOrchestratorService.listTasks());
    }

    @GetMapping("/{taskId}")
    public ApiResponse<TaskDetailResponse> detail(@PathVariable String taskId) {
        return ApiResponse.success(workflowOrchestratorService.getTaskDetail(taskId));
    }

    @PostMapping("/{taskId}/regenerate")
    public ApiResponse<Void> regenerate(
            @PathVariable String taskId,
            @RequestBody(required = false) RegenerateVideoTaskRequest request
    ) {
        workflowOrchestratorService.regenerate(taskId, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/{taskId}/select-assets")
    public ApiResponse<Void> selectAssets(
            @PathVariable String taskId,
            @RequestBody SelectAssetsRequest request
    ) {
        workflowOrchestratorService.selectAssets(taskId, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/{taskId}/context")
    public ApiResponse<Void> updateContext(
            @PathVariable String taskId,
            @RequestBody UpdateWorkflowContextRequest request
    ) {
        workflowOrchestratorService.updateContext(taskId, request);
        return ApiResponse.success(null);
    }
}
