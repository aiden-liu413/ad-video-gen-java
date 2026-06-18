package com.volcengine.demo.advideo.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptServiceTest {

    private final PromptService promptService = new PromptService();

    @Test
    void loadsAllPromptsFromClasspath() {
        assertThat(promptService.marketAgent()).isNotBlank();
        assertThat(promptService.directorStoryboardAgent()).isNotBlank();
        assertThat(promptService.evaluateAgent()).isNotBlank();
        assertThat(promptService.releaseAgent()).isNotBlank();
    }
}
