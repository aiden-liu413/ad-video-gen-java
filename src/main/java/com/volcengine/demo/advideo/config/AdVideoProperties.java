package com.volcengine.demo.advideo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ad-video")
public record AdVideoProperties(
        ModelEndpoint llm,
        ModelEndpoint image,
        ModelEndpoint video,
        FileUpload fileUpload,
        ShortLink shortLink,
        Ffmpeg ffmpeg
) {
    public record ModelEndpoint(String baseUrl, String apiKey, String model, String endpointId) {
    }

    public record ShortLink(String publicBaseUrl) {
    }

    public record FileUpload(String purpose) {
    }

    public record Ffmpeg(String binary, String outputDir) {
    }
}
