package com.volcengine.demo.advideo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.demo.advideo.dto.GenerateRequest;
import com.volcengine.demo.advideo.dto.GenerationCheckpoint;
import com.volcengine.demo.advideo.dto.GenerationResult;
import com.volcengine.demo.advideo.dto.TaskResponse;
import com.volcengine.demo.advideo.entity.AdVideoTaskEntity;
import com.volcengine.demo.advideo.orchestrator.AdVideoOrchestrator;
import com.volcengine.demo.advideo.repository.AdVideoTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final AdVideoOrchestrator orchestrator;
    private final AdVideoTaskRepository taskRepository;
    private final ObjectMapper objectMapper;

    public TaskService(
            AdVideoOrchestrator orchestrator,
            AdVideoTaskRepository taskRepository,
            ObjectMapper objectMapper
    ) {
        this.orchestrator = orchestrator;
        this.taskRepository = taskRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TaskResponse submit(GenerateRequest request) {
        String taskId = UUID.randomUUID().toString();
        GenerationCheckpoint checkpoint = new GenerationCheckpoint();
        AdVideoTaskEntity entity = new AdVideoTaskEntity();
        Instant now = Instant.now();
        entity.setTaskId(taskId);
        entity.setStatus("RUNNING");
        entity.setRequestJson(toJson(request));
        entity.setCheckpointJson(toJson(checkpoint));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        taskRepository.save(entity);

        log.info("Submit ad video task, taskId={}, productName={}", taskId, request.productName());
        runAsyncAfterCommit(taskId);
        return toResponse(entity);
    }

    @Transactional
    public TaskResponse retry(String taskId) {
        AdVideoTaskEntity entity = taskRepository.findById(taskId).orElse(null);
        if (entity == null) {
            log.warn("Retry ad video task failed: task not found, taskId={}", taskId);
            return new TaskResponse(taskId, "NOT_FOUND", null, "task not found", null);
        }
        if ("RUNNING".equals(entity.getStatus())) {
            log.info("Retry ad video task ignored: task is running, taskId={}, currentStep={}",
                    taskId, entity.getCurrentStep());
            return toResponse(entity);
        }
        if ("SUCCEEDED".equals(entity.getStatus())) {
            log.info("Retry ad video task ignored: task already succeeded, taskId={}", taskId);
            return toResponse(entity);
        }

        entity.setStatus("RUNNING");
        entity.setError(null);
        entity.setUpdatedAt(Instant.now());
        taskRepository.save(entity);
        log.info("Retry ad video task from persisted checkpoint, taskId={}, lastStep={}",
                taskId, entity.getCurrentStep());
        runAsyncAfterCommit(taskId);
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public TaskResponse get(String taskId) {
        AdVideoTaskEntity entity = taskRepository.findById(taskId).orElse(null);
        if (entity == null) {
            log.warn("Ad video task not found, taskId={}", taskId);
            return new TaskResponse(taskId, "NOT_FOUND", null, "task not found", null);
        }
        log.info("Ad video task status, taskId={}, status={}, currentStep={}",
                taskId, entity.getStatus(), entity.getCurrentStep());
        return toResponse(entity);
    }

    public void run(String taskId) {
        AdVideoTaskEntity entity = taskRepository.findById(taskId).orElse(null);
        if (entity == null) {
            log.warn("Run ad video task aborted: task not found, taskId={}", taskId);
            return;
        }

        GenerateRequest request = fromJson(entity.getRequestJson(), GenerateRequest.class);
        GenerationCheckpoint checkpoint = fromJson(entity.getCheckpointJson(), GenerationCheckpoint.class);
        try {
            log.info("Start ad video task, taskId={}, persistedCheckpointStep={}",
                    taskId, checkpoint.getCurrentStep());
            GenerationResult result = orchestrator.generate(taskId, request, checkpoint, saved -> saveCheckpoint(taskId, saved));
            markSucceeded(taskId, checkpoint, result);
            log.info("Ad video task succeeded, taskId={}, videoUrl={}", taskId, result.multimedia().videoUrl());
        } catch (RuntimeException ex) {
            markFailed(taskId, checkpoint, ex);
            log.error("Ad video task failed, taskId={}, failedStep={}",
                    taskId, checkpoint.getCurrentStep(), ex);
        }
    }

    @Transactional
    public void saveCheckpoint(String taskId, GenerationCheckpoint checkpoint) {
        taskRepository.findById(taskId).ifPresent(entity -> {
            entity.setCheckpointJson(toJson(checkpoint));
            entity.setCurrentStep(checkpoint.getCurrentStep());
            entity.setUpdatedAt(Instant.now());
            taskRepository.save(entity);
        });
    }

    @Transactional
    protected void markSucceeded(String taskId, GenerationCheckpoint checkpoint, GenerationResult result) {
        AdVideoTaskEntity entity = taskRepository.findById(taskId).orElseThrow();
        entity.setStatus("SUCCEEDED");
        entity.setCurrentStep(checkpoint.getCurrentStep());
        entity.setCheckpointJson(toJson(checkpoint));
        entity.setResultJson(toJson(result));
        entity.setError(null);
        entity.setUpdatedAt(Instant.now());
        taskRepository.save(entity);
    }

    @Transactional
    protected void markFailed(String taskId, GenerationCheckpoint checkpoint, RuntimeException ex) {
        AdVideoTaskEntity entity = taskRepository.findById(taskId).orElseThrow();
        entity.setStatus("FAILED");
        entity.setCurrentStep(checkpoint.getCurrentStep());
        entity.setCheckpointJson(toJson(checkpoint));
        entity.setError(ex.getMessage());
        entity.setUpdatedAt(Instant.now());
        taskRepository.save(entity);
    }

    private TaskResponse toResponse(AdVideoTaskEntity entity) {
        GenerationResult result = entity.getResultJson() == null
                ? null
                : fromJson(entity.getResultJson(), GenerationResult.class);
        return new TaskResponse(
                entity.getTaskId(),
                entity.getStatus(),
                result,
                entity.getError(),
                entity.getCurrentStep()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON serialization failed", ex);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON deserialization failed: " + type.getSimpleName(), ex);
        }
    }

    private void runAsyncAfterCommit(String taskId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            CompletableFuture.runAsync(() -> run(taskId));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("Transaction committed, start async ad video task, taskId={}", taskId);
                CompletableFuture.runAsync(() -> run(taskId));
            }
        });
    }
}
