package com.volcengine.demo.advideo.service;

import com.volcengine.demo.advideo.entity.AdVideoTaskEntity;
import com.volcengine.demo.advideo.repository.AdVideoTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class TaskRecoveryService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TaskRecoveryService.class);

    private final AdVideoTaskRepository taskRepository;

    public TaskRecoveryService(AdVideoTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<AdVideoTaskEntity> runningTasks = taskRepository.findByStatus("RUNNING");
        if (runningTasks.isEmpty()) {
            return;
        }
        for (AdVideoTaskEntity task : runningTasks) {
            task.setStatus("FAILED");
            task.setError("服务重启导致任务中断，可调用 retry 从 checkpoint 继续。");
            task.setUpdatedAt(Instant.now());
        }
        taskRepository.saveAll(runningTasks);
        log.warn("Recovered interrupted ad video tasks, count={}", runningTasks.size());
    }
}
