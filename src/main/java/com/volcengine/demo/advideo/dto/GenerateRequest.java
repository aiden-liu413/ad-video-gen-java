package com.volcengine.demo.advideo.dto;

import java.util.List;

public record GenerateRequest(
        String prompt,
        String productName,
        String productDescription,
        String productUrl,
        String targetAudience,
        String sellingPoints,
        String style,
        String duration,
        String landingPageUrl,
        List<String> referenceImageUrls
) {
}
