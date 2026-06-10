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

    private static final Pattern PYTHON_TRIPLE_QUOTED_CONSTANT = Pattern.compile(
            "(PROMPT_[A-Z0-9_]+)\\s*=\\s*\"\"\"(.*?)\"\"\"",
            Pattern.DOTALL
    );

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String marketAgent() {
        return get("prompts/market-agent/prompt.py", "PROMPT_MARKET_AGENT");
    }

    public String directorStoryboardAgent() {
        return get("prompts/director-agent/prompt.py", "PROMPT_STORYBOARD_AGENT");
    }

    public String directorImageAgent() {
        return get("prompts/director-agent/prompt.py", "PROMPT_IMAGE_AGENT");
    }

    public String directorVideoAgent() {
        return get("prompts/director-agent/prompt.py", "PROMPT_VIDEO_AGENT");
    }

    public String evaluateAgent() {
        return get("prompts/evaluate-agent/prompt.py", "PROMPT_EVALUATE_AGENT");
    }

    public String releaseAgent() {
        return get("prompts/release-agent/prompt.py", "PROMPT_RELEASE_AGENT");
    }

    public String multimediaRootAgent() {
        return get("prompts/multimedia-agent/prompt.py", "PROMPT_ROOT_AGENT");
    }

    private String get(String resourcePath, String constantName) {
        return cache.computeIfAbsent(resourcePath + "#" + constantName, ignored -> load(resourcePath, constantName));
    }

    private String load(String resourcePath, String constantName) {
        try {
            String source = new ClassPathResource(resourcePath).getContentAsString(StandardCharsets.UTF_8);
            Matcher matcher = PYTHON_TRIPLE_QUOTED_CONSTANT.matcher(source);
            while (matcher.find()) {
                if (constantName.equals(matcher.group(1))) {
                    return matcher.group(2).trim();
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Prompt resource not found: " + resourcePath, ex);
        }
        throw new IllegalStateException("Prompt constant not found: " + constantName + " in " + resourcePath);
    }
}
