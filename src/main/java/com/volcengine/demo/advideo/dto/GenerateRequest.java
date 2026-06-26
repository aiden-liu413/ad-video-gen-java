package com.volcengine.demo.advideo.dto;

import java.util.List;

public record GenerateRequest(
        String prompt,
        String productName,
        String productDescription,
        String targetAudience,
        String sellingPoints,
        String style,
        String duration,
        Boolean voiceoverDisabled,
        List<String> referenceImageUrls,
        List<String> referenceImageFileIds
) {
    public boolean voiceoverDisabledValue() {
        return Boolean.TRUE.equals(voiceoverDisabled);
    }
}
