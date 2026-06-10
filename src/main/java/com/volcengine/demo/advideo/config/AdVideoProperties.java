package com.volcengine.demo.advideo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ad-video")
public record AdVideoProperties(
        ModelEndpoint llm,
        ModelEndpoint image,
        ModelEndpoint video,
        ShortLink shortLink,
        Upload upload
) {
    public record ModelEndpoint(String baseUrl, String apiKey, String model, String endpointId, boolean enabled) {
    }

    public record ShortLink(String publicBaseUrl) {
    }

    public record Upload(String storageDir) {
    }
}
