package com.volcengine.demo.advideo.client;

import com.volcengine.demo.advideo.config.AdVideoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
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
        if (!StringUtils.hasText(properties.llm().apiKey())) {
            log.info("LLM api key missing, use local completion fallback, model={}", modelName());
            return localCompletion(systemPrompt, userPrompt);
        }

        Map<String, Object> body = Map.of(
                "model", modelName(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        try {
            log.info("Call LLM chat completion, model={}, promptChars={}", modelName(), userPrompt == null ? 0 : userPrompt.length());
            ArkResponse response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.llm().apiKey())
                    .body(body)
                    .retrieve()
                    .body(ArkResponse.class);
            if (response != null && response.choices() != null && !response.choices().isEmpty()) {
                log.info("LLM chat completion succeeded, model={}", modelName());
                return response.choices().get(0).message().content();
            }
        } catch (RestClientResponseException ex) {
            log.error("LLM chat completion API failed, model={}, statusCode={}, responseBody={}",
                    modelName(), ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            return "LLM 调用失败，已使用本地兜底结果。错误：" + ex.getMessage();
        } catch (RuntimeException ex) {
            log.error("LLM chat completion failed, use local fallback, model={}", modelName(), ex);
            return "LLM 调用失败，已使用本地兜底结果。错误：" + ex.getMessage();
        }
        log.warn("LLM chat completion returned empty response, use local fallback, model={}", modelName());
        return localCompletion(systemPrompt, userPrompt);
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

    public record ArkResponse(List<Choice> choices) {
    }

    public record Choice(Message message) {
    }

    public record Message(String content) {
    }
}
