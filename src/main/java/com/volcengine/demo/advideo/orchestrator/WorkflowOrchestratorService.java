package com.volcengine.demo.advideo.orchestrator;

import com.volcengine.demo.advideo.dto.CreateVideoTaskRequest;
import com.volcengine.demo.advideo.dto.RegenerateVideoTaskRequest;
import com.volcengine.demo.advideo.dto.SelectAssetsRequest;
import com.volcengine.demo.advideo.dto.TaskDetailResponse;
import com.volcengine.demo.advideo.dto.TaskSummary;
import com.volcengine.demo.advideo.dto.UpdateWorkflowContextRequest;

import java.util.List;

public interface WorkflowOrchestratorService {

    String createTask(CreateVideoTaskRequest request);

    void startAsync(String taskId);

    void run(String taskId);

    void advanceAsync(String taskId);

    void retryFromStage(String taskId, String stage);

    void regenerate(String taskId, RegenerateVideoTaskRequest request);

    void selectAssets(String taskId, SelectAssetsRequest request);

    void updateContext(String taskId, UpdateWorkflowContextRequest request);

    TaskDetailResponse getTaskDetail(String taskId);

    List<TaskSummary> listTasks();
}
