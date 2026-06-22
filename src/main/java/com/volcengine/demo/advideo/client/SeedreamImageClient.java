package com.volcengine.demo.advideo.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.volcengine.demo.advideo.config.AdVideoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class SeedreamImageClient {

    private static final Logger log = LoggerFactory.getLogger(SeedreamImageClient.class);

    private final AdVideoProperties properties;
    private final RestClient restClient;

    public SeedreamImageClient(AdVideoProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 连接超时：建立 TCP 连接的最大等待时间
        factory.setConnectTimeout(Duration.ofSeconds(3));
        // 读取超时：连接建立后，等待服务端返回数据的最大时间
        factory.setReadTimeout(Duration.ofSeconds(1800));
        this.restClient = restClientBuilder.requestFactory(factory).baseUrl(properties.image().baseUrl()).build();
    }

    public List<String> generateImages(String prompt, List<String> referenceImageUrls, int imageCount) {
        int maxImages = Math.max(1, imageCount);
        if (!StringUtils.hasText(properties.image().apiKey())) {
            log.info("Seedream api key missing, use mock images, maxImages={}, referenceImageCount={}",
                    maxImages, referenceImageUrls == null ? 0 : referenceImageUrls.size());
            return java.util.stream.IntStream.rangeClosed(1, maxImages)
                    .mapToObj(index -> "mock://seedream/images/" + Math.abs((prompt + index).hashCode()) + ".png")
                    .toList();
        }

        log.info("Seedream image generation start, model={}, maxImages={}, referenceImageCount={}",
                modelName(), maxImages, referenceImageUrls == null ? 0 : referenceImageUrls.size());
        List<String> imageUrls = generateImageGroup(prompt, referenceImageUrls, maxImages);
        log.info("Seedream image generation done, model={}, imageCount={}", modelName(), imageUrls.size());
        return imageUrls;
    }

    private List<String> generateImageGroup(String prompt, List<String> referenceImageUrls, int maxImages) {
        Map<String, Object> payload = Map.of(
                "model", modelName(),
                "prompt", prompt,
                "image", imageInputs(referenceImageUrls),
                "sequential_image_generation", "auto",
                "sequential_image_generation_options", Map.of("max_images", maxImages),
                "watermark", false
        );
        ImageResponse response;
        try {
            response = restClient.post()
                    .uri("/images/generations")
                    .header("Authorization", "Bearer " + properties.image().apiKey())
                    .body(payload)
                    .retrieve()
                    .body(ImageResponse.class);
        } catch (RestClientResponseException ex) {
            log.error("Seedream image generation API failed, model={}, maxImages={}, statusCode={}, responseBody={}",
                    modelName(), maxImages, ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Seedream image generation API failed, model={}, maxImages={}", modelName(), maxImages, ex);
            throw ex;
        }
        log.info("Seedream image group generated, requestModel={}, responseModel={}, maxImages={}, generatedImages={}, outputTokens={}, totalTokens={}, watermark=false",
                modelName(),
                response == null ? "" : response.model(),
                maxImages,
                response == null || response.usage() == null ? null : response.usage().generatedImages(),
                response == null || response.usage() == null ? null : response.usage().outputTokens(),
                response == null || response.usage() == null ? null : response.usage().totalTokens());
        return imageUrls(response, prompt, maxImages);
    }

    private List<Object> imageInputs(List<String> referenceImageUrls) {
        if (referenceImageUrls == null || referenceImageUrls.isEmpty()) {
            return List.of();
        }
        return referenceImageUrls.stream()
                .filter(StringUtils::hasText)
                .map(this::imageInput)
                .toList();
    }

    private Object imageInput(String value) {
        if (value.startsWith("fileid:")) {
            return Map.of("file_id", value.substring("fileid:".length()));
        }
        return value;
    }

    private String modelName() {
        return StringUtils.hasText(properties.image().endpointId())
                ? properties.image().endpointId()
                : properties.image().model();
    }

    private List<String> imageUrls(ImageResponse response, String prompt, int maxImages) {
        if (response == null || response.data() == null || response.data().isEmpty()) {
            return java.util.stream.IntStream.rangeClosed(1, maxImages)
                    .mapToObj(index -> "mock://seedream/images/" + Math.abs((prompt + index).hashCode()) + ".png")
                    .toList();
        }
        List<String> imageUrls = response.data().stream()
                .map(ImageData::url)
                .filter(StringUtils::hasText)
                .limit(maxImages)
                .toList();
        if (imageUrls.size() >= maxImages) {
            return imageUrls;
        }
        java.util.ArrayList<String> filled = new java.util.ArrayList<>(imageUrls);
        for (int index = filled.size() + 1; index <= maxImages; index++) {
            filled.add("mock://seedream/images/" + Math.abs((prompt + index).hashCode()) + ".png");
        }
        return filled;
    }

    public record ImageResponse(
            String model,
            Long created,
            List<ImageData> data,
            ImageUsage usage
    ) {
    }

    public record ImageData(String url, String size) {
    }

    public record ImageUsage(
            @JsonProperty("generated_images") Integer generatedImages,
            @JsonProperty("output_tokens") Integer outputTokens,
            @JsonProperty("total_tokens") Integer totalTokens
    ) {
    }
}
