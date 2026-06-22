package com.volcengine.demo.advideo.client;

import com.volcengine.demo.advideo.config.AdVideoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ArkChatClient {

    private static final Logger log = LoggerFactory.getLogger(ArkChatClient.class);

    private final AdVideoProperties properties;
    private final RestClient restClient;

    public ArkChatClient(AdVideoProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.llm().baseUrl()).build();
    }

    public String complete(String systemPrompt, String userPrompt) {
        return complete(systemPrompt, userPrompt, List.of());
    }

    public String complete(String systemPrompt, String userPrompt, List<String> imageUrls) {
        return complete(systemPrompt, userPrompt, imageUrls, List.of());
    }

    public String complete(String systemPrompt, String userPrompt, List<String> imageUrls, List<String> imageFileIds) {
        List<MediaInput> mediaInputs = new ArrayList<>();
        if (imageUrls != null) {
            mediaInputs.addAll(imageUrls.stream()
                    .filter(StringUtils::hasText)
                    .map(MediaInput::imageUrl)
                    .toList());
        }
        if (imageFileIds != null) {
            mediaInputs.addAll(imageFileIds.stream()
                    .filter(StringUtils::hasText)
                    .map(MediaInput::imageFile)
                    .toList());
        }
        return completeWithMedia(systemPrompt, userPrompt, mediaInputs);
    }

    public String completeWithMedia(String systemPrompt, String userPrompt, List<MediaInput> mediaInputs) {
        if (!StringUtils.hasText(properties.llm().apiKey())) {
            log.info("LLM api key missing, use local completion fallback, model={}", modelName());
            return localCompletion(systemPrompt, userPrompt);
        }

        Object userContent = buildUserContent(userPrompt, mediaInputs);
        Map<String, Object> body = Map.of(
                "model", modelName(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userContent)
                )
        );

        try {
            log.info("Call LLM chat completion, model={}, promptChars={}, imageUrlCount={}",
                    modelName(),
                    userPrompt == null ? 0 : userPrompt.length(),
                    mediaInputs == null ? 0 : mediaInputs.size());
            ArkResponse response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.llm().apiKey())
                    .body(body)
                    .retrieve()
                    .body(ArkResponse.class);
            if (response != null && response.choices() != null && !response.choices().isEmpty()) {
                log.info("LLM chat completion succeeded, model={}, imageUrlCount={}",
                        modelName(),
                        mediaInputs == null ? 0 : mediaInputs.size());
                return response.choices().get(0).message().content();
            }
        } catch (RestClientResponseException ex) {
            log.error("LLM chat completion API failed, model={}, imageUrlCount={}, statusCode={}, responseBody={}",
                    modelName(),
                    mediaInputs == null ? 0 : mediaInputs.size(),
                    ex.getStatusCode(),
                    ex.getResponseBodyAsString(),
                    ex);
            return "LLM 调用失败，已使用本地兜底结果。错误：" + ex.getMessage();
        } catch (RuntimeException ex) {
            log.error("LLM chat completion failed, use local fallback, model={}, imageUrlCount={}",
                    modelName(),
                    mediaInputs == null ? 0 : mediaInputs.size(),
                    ex);
            return "LLM 调用失败，已使用本地兜底结果。错误：" + ex.getMessage();
        }
        log.warn("LLM chat completion returned empty response, use local fallback, model={}", modelName());
        return localCompletion(systemPrompt, userPrompt);
    }

    private Object buildUserContent(String userPrompt, List<MediaInput> mediaInputs) {
        if (mediaInputs == null || mediaInputs.isEmpty()) {
            return userPrompt;
        }
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", valueOrEmpty(userPrompt)));
        for (MediaInput mediaInput : mediaInputs) {
            Map<String, Object> part = mediaInput.toContentPart();
            if (part != null && !part.isEmpty()) {
                content.add(part);
            }
        }
        return content;
    }

    private String modelName() {
        return StringUtils.hasText(properties.llm().endpointId())
                ? properties.llm().endpointId()
                : properties.llm().model();
    }

    private String localCompletion(String systemPrompt, String userPrompt) {
        String role = systemPrompt.length() > 18 ? systemPrompt.substring(0, 18) : systemPrompt;
        return "本地模拟输出 [" + role + "]：基于输入生成结构化营销建议。输入摘要：" + summarize(userPrompt);
    }

    private String summarize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() > 140 ? normalized.substring(0, 140) + "..." : normalized;
    }

    private String valueOrEmpty(String text) {
        return text == null ? "" : text;
    }

    public record ArkResponse(List<Choice> choices) {
    }

    public record Choice(Message message) {
    }

    public record Message(String content) {
    }

    public record MediaInput(String type, String url, String fileId) {

        public static MediaInput imageUrl(String url) {
            return new MediaInput("image_url", url, null);
        }

        public static MediaInput imageFile(String fileId) {
            return new MediaInput("image_url", null, fileId);
        }

        public static MediaInput videoUrl(String url) {
            return new MediaInput("video_url", url, null);
        }

        public static MediaInput videoFile(String fileId) {
            return new MediaInput("video_url", null, fileId);
        }

        public Map<String, Object> toContentPart() {
            if (!StringUtils.hasText(type)) {
                return Map.of();
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            if (StringUtils.hasText(url)) {
                payload.put("url", url);
            }
            if (StringUtils.hasText(fileId)) {
                payload.put("file_id", fileId);
            }
            return payload.isEmpty()
                    ? Map.of()
                    : Map.of(typeKey(), type, type, payload);
        }

        private String typeKey() {
            return "type";
        }
    }
}
