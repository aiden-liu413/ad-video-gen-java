package com.volcengine.demo.advideo.domain.model;

import java.math.BigDecimal;

public record VideoCandidate(
        String assetId,
        String shotId,
        Integer id,
        String url,
        BigDecimal score,
        String reason,
        Boolean selected
) {
}
