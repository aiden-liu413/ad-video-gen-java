package com.volcengine.demo.advideo.repository;

import com.volcengine.demo.advideo.entity.ShortLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortLinkRepository extends JpaRepository<ShortLinkEntity, String> {
}
