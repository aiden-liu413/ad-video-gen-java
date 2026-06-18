package com.volcengine.demo.advideo.service;

import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
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

    public String evaluateAgent() {
        return get("prompts/evaluate-agent/prompt.md", "PROMPT_EVALUATE_AGENT");
    }

    public String releaseAgent() {
        return get("prompts/release-agent/prompt.md", "PROMPT_RELEASE_AGENT");
    }

    private String get(String resourcePath, String constantName) {
        return cache.computeIfAbsent(resourcePath + "#" + constantName, ignored -> load(resourcePath, constantName));
    }

    private String load(String resourcePath, String constantName) {
        try {
            String source = readClasspathResource(resourcePath);
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

    /**
     * Reads a prompt directly from the active classpath so the same code works from IDE classes
     * and from resources nested inside a Spring Boot executable JAR.
     */
    private String readClasspathResource(String resourcePath) throws IOException {
        try (InputStream inputStream = openClasspathResource(resourcePath)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private InputStream openClasspathResource(String resourcePath) throws IOException {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        InputStream inputStream = contextClassLoader == null
                ? null
                : contextClassLoader.getResourceAsStream(resourcePath);
        if (inputStream == null) {
            inputStream = PromptService.class.getClassLoader().getResourceAsStream(resourcePath);
        }
        if (inputStream == null) {
            throw new FileNotFoundException(resourcePath);
        }
        return inputStream;
    }
}
