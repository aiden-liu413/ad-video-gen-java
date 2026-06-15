package com.volcengine.demo.advideo.domain.model;

import java.math.BigDecimal;

public record ImageCandidate(
        String assetId,
        String shotId,
        Integer id,
        String url,
        BigDecimal score,
        String reason,
        Boolean selected
) {
}
