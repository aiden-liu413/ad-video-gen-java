package com.volcengine.demo.advideo.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.volcengine.demo.advideo.config.AdVideoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class SeedanceVideoClient {

    private static final Logger log = LoggerFactory.getLogger(SeedanceVideoClient.class);
    private static final int MAX_QUERY_ATTEMPTS = 120;
    private static final long QUERY_INTERVAL_MILLIS = 5_000L;

    private final AdVideoProperties properties;
    private final RestClient restClient;

    public SeedanceVideoClient(AdVideoProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.video().baseUrl()).build();
    }

    public VideoGeneration generateVideo(String productName, List<String> imageUrls, String script, int durationSeconds, String ratio) {
        if (!properties.video().enabled() || !StringUtils.hasText(properties.video().apiKey())) {
            String taskId = UUID.randomUUID().toString();
            String videoUrl = properties.shortLink().publicBaseUrl() + "/mock/seedance/videos/" + taskId + ".mp4";
            log.info("Seedance disabled or api key missing, use mock video, taskId={}, imageCount={}", taskId, imageUrls.size());
            return new VideoGeneration(taskId, videoUrl);
        }

        Map<String, Object> payload = Map.of(
                "model", modelName(),
                "content", buildContentForSwz(script, imageUrls),
                "ratio", ratioValue(ratio),
                "duration", durationSeconds,
                "watermark", false,
                "task_type","i2v",
                "metadata", Map.of("productName", productName)
        );
        log.info("Seedance video generation start, model={}, productName={}, imageCount={}, duration={}, ratio={}, scriptChars={}",
                modelName(), productName, imageUrls.size(), durationSeconds, ratioValue(ratio), script == null ? 0 : script.length());
        VideoTaskResponse response;
        try {
            response = restClient.post()
                    .uri("/contents/generations/tasks")
                    .header("Authorization", "Bearer " + properties.video().apiKey())
                    .body(payload)
                    .retrieve()
                    .body(VideoTaskResponse.class);
        } catch (RestClientResponseException ex) {
            log.error("Seedance video generation API failed, model={}, productName={}, statusCode={}, responseBody={}",
                    modelName(), productName, ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Seedance video generation API failed, model={}, productName={}", modelName(), productName, ex);
            throw ex;
        }
        if (response == null || !StringUtils.hasText(response.id())) {
            log.warn("Seedance video generation returned empty response, model={}", modelName());
            return new VideoGeneration("", "");
        }
        log.info("Seedance video generation submitted, model={}, seedanceTaskId={}", modelName(), response.id());
        return queryUntilCompleted(response.id());
    }

    private VideoGeneration queryUntilCompleted(String taskId) {
        for (int attempt = 1; attempt <= MAX_QUERY_ATTEMPTS; attempt++) {
            VideoTaskResponse response = queryTask(taskId);
            String status = response == null ? "" : response.status();
            log.info("Seedance video generation query, seedanceTaskId={}, attempt={}/{}, status={}",
                    taskId, attempt, MAX_QUERY_ATTEMPTS, status);
            if ("succeeded".equalsIgnoreCase(status)) {
                String videoUrl = response.content() == null ? "" : response.content().videoUrl();
                if (!StringUtils.hasText(videoUrl)) {
                    throw new IllegalStateException("Seedance task succeeded but video_url is empty, taskId=" + taskId);
                }
                log.info("Seedance video generation succeeded, seedanceTaskId={}, videoUrl={}", taskId, videoUrl);
                return new VideoGeneration(taskId, videoUrl);
            }
            if ("failed".equalsIgnoreCase(status)
                    || "cancelled".equalsIgnoreCase(status)
                    || "canceled".equalsIgnoreCase(status)) {
                throw new IllegalStateException("Seedance task ended with status=" + status + ", taskId=" + taskId);
            }
            sleepBeforeNextQuery(taskId);
        }
        throw new IllegalStateException("Seedance task query timeout, taskId=" + taskId);
    }

    private VideoTaskResponse queryTask(String taskId) {
        try {
            return restClient.get()
                    .uri("/contents/generations/tasks/{id}", taskId)
                    .header("Authorization", "Bearer " + properties.video().apiKey())
                    .retrieve()
                    .body(VideoTaskResponse.class);
        } catch (RestClientResponseException ex) {
            log.error("Seedance video task query API failed, model={}, seedanceTaskId={}, statusCode={}, responseBody={}",
                    modelName(), taskId, ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Seedance video task query API failed, model={}, seedanceTaskId={}", modelName(), taskId, ex);
            throw ex;
        }
    }

    private void sleepBeforeNextQuery(String taskId) {
        try {
            Thread.sleep(QUERY_INTERVAL_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Seedance task, taskId=" + taskId, ex);
        }
    }

    private List<Map<String, Object>> buildContentForSwz(String script, List<String> imageUrls) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", script == null ? "" : script));
        if(imageUrls.size() > 0) {
            if(imageUrls.size() > 1) {
                content.add(Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", imageUrls.get(0)),
                        "role", "first_frame"

                ));
                content.add(Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", imageUrls.get(1)),
                        "role", "last_frame"

                ));
            }else{
                content.add(Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", imageUrls.get(0))
                ));
            }
        }

        log.info("Seedance request content built, textCount=1, imageUrlCount={}", content.size() - 1);
        return content;
    }

    private String modelName() {
        return StringUtils.hasText(properties.video().endpointId())
                ? properties.video().endpointId()
                : properties.video().model();
    }

    private String ratioValue(String ratio) {
        return StringUtils.hasText(ratio) ? ratio : "adaptive";
    }

    public record VideoGeneration(String taskId, String videoUrl) {
    }

    public record VideoTaskResponse(String id, String status, VideoContent content) {
    }

    public record VideoContent(@JsonProperty("video_url") String videoUrl) {
    }
}
