package com.volcengine.demo.advideo.repository;

import com.volcengine.demo.advideo.entity.AdVideoTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdVideoTaskRepository extends JpaRepository<AdVideoTaskEntity, String> {

    List<AdVideoTaskEntity> findByStatus(String status);
}
