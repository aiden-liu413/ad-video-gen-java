package com.volcengine.demo.advideo.domain.model;

import java.util.List;
import java.util.Map;

public record ProductInfo(
        String name,
        String sellingPoint,
        List<String> resources,
        String price,
        String brand,
        Map<String, Object> extra
) {
}
