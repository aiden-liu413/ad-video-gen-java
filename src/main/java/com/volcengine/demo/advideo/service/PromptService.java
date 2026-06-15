package com.volcengine.demo.advideo.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PromptService {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String marketAgent() {
        return get("prompts/market-agent/prompt.md", "PROMPT_MARKET_AGENT");
    }

    public String directorStoryboardAgent() {
        return get("prompts/director-agent/prompt.md", "PROMPT_STORYBOARD_AGENT");
    }

    public String directorImageAgent() {
        return get("prompts/director-agent/prompt.md", "PROMPT_IMAGE_AGENT");
    }

    public String directorVideoAgent() {
        return get("prompts/director-agent/prompt.md", "PROMPT_VIDEO_AGENT");
    }

    public String evaluateAgent() {
        return get("prompts/evaluate-agent/prompt.md", "PROMPT_EVALUATE_AGENT");
    }

    public String releaseAgent() {
        return get("prompts/release-agent/prompt.md", "PROMPT_RELEASE_AGENT");
    }

    public String multimediaRootAgent() {
        return get("prompts/multimedia-agent/prompt.md", "PROMPT_ROOT_AGENT");
    }

    private String get(String resourcePath, String constantName) {
        return cache.computeIfAbsent(resourcePath + "#" + constantName, ignored -> load(resourcePath, constantName));
    }

    private String load(String resourcePath, String constantName) {
        try {
            String source = new ClassPathResource(resourcePath).getContentAsString(StandardCharsets.UTF_8);
            Pattern sectionPattern = Pattern.compile(
                    "^##\\s+" + Pattern.quote(constantName) + "\\s*$([\\s\\S]*?)(?=^##\\s+PROMPT_[A-Z0-9_]+\\s*$|\\z)",
                    Pattern.MULTILINE
            );
            Matcher matcher = sectionPattern.matcher(source);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Prompt resource not found: " + resourcePath, ex);
        }
        throw new IllegalStateException("Prompt section not found: " + constantName + " in " + resourcePath);
    }
}
