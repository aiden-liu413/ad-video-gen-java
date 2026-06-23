package com.volcengine.demo.advideo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ad-video")
public record AdVideoProperties(
        ModelEndpoint llm,
        ModelEndpoint image,
        ModelEndpoint video,
        Storage storage,
        ShortLink shortLink,
        Ffmpeg ffmpeg
) {
    public record ModelEndpoint(String baseUrl, String apiKey, String model, String endpointId) {
    }

    public record ShortLink(String publicBaseUrl) {
    }

    public record Storage(
            String endpoint,
            String publicBaseUrl,
            String accessKey,
            String secretKey,
            String bucket,
            String region,
            Boolean pathStyleAccess,
            Boolean autoCreateBucket,
            Integer objectExpirationDays
    ) {
    }

    public record Ffmpeg(String binary, String outputDir) {
    }
}
