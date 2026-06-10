package com.volcengine.demo.advideo.client;

import com.volcengine.demo.advideo.config.AdVideoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class SeedreamImageClient {

    private static final Logger log = LoggerFactory.getLogger(SeedreamImageClient.class);

    private final AdVideoProperties properties;
    private final RestClient restClient;

    public SeedreamImageClient(AdVideoProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.image().baseUrl()).build();
    }

    public List<String> generateImages(List<String> prompts, List<String> referenceImageUrls) {
        if (!properties.image().enabled() || !StringUtils.hasText(properties.image().apiKey())) {
            log.info("Seedream disabled or api key missing, use mock images, promptCount={}, referenceImageCount={}",
                    prompts.size(), referenceImageUrls == null ? 0 : referenceImageUrls.size());
            return prompts.stream()
                    .map(prompt -> "mock://seedream/images/" + Math.abs(prompt.hashCode()) + ".png")
                    .toList();
        }

        List<String> imageUrls = new ArrayList<>();
        log.info("Seedream image generation start, model={}, promptCount={}, referenceImageCount={}",
                modelName(), prompts.size(), referenceImageUrls == null ? 0 : referenceImageUrls.size());
        for (String prompt : prompts) {
            Map<String, Object> payload = Map.of(
                    "model", modelName(),
                    "prompt", prompt,
                    "reference_image_urls", referenceImageUrls == null ? List.of() : referenceImageUrls,
                    "watermark", false
            );
            ImageResponse response = restClient.post()
                    .uri("/images/generations")
                    .header("Authorization", "Bearer " + properties.image().apiKey())
                    .body(payload)
                    .retrieve()
                    .body(ImageResponse.class);
            imageUrls.add(firstImageUrl(response, prompt));
            log.info("Seedream image generated, model={}, generatedCount={}/{}, watermark=false",
                    modelName(), imageUrls.size(), prompts.size());
        }
        log.info("Seedream image generation done, model={}, imageCount={}", modelName(), imageUrls.size());
        return imageUrls;
    }

    private String modelName() {
        return StringUtils.hasText(properties.image().endpointId())
                ? properties.image().endpointId()
                : properties.image().model();
    }

    private String firstImageUrl(ImageResponse response, String prompt) {
        if (response == null || response.data() == null || response.data().isEmpty()) {
            return "mock://seedream/images/" + Math.abs(prompt.hashCode()) + ".png";
        }
        return response.data().get(0).url();
    }

    public record ImageResponse(List<ImageData> data) {
    }

    public record ImageData(String url) {
    }
}
