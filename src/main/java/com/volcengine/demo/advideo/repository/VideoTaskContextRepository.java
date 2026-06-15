package com.volcengine.demo.advideo.repository;

import com.volcengine.demo.advideo.entity.VideoTaskContextEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VideoTaskContextRepository extends JpaRepository<VideoTaskContextEntity, Long> {

    Optional<VideoTaskContextEntity> findByTaskId(String taskId);
}
