package com.volcengine.demo.advideo.repository;

import com.volcengine.demo.advideo.entity.VideoTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VideoTaskRepository extends JpaRepository<VideoTaskEntity, Long> {

    Optional<VideoTaskEntity> findByTaskId(String taskId);

    List<VideoTaskEntity> findTop20ByOrderByUpdatedAtDesc();
}
