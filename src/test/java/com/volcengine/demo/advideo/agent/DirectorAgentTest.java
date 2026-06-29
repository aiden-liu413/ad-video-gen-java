package com.volcengine.demo.advideo.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.demo.advideo.client.ArkChatClient;
import com.volcengine.demo.advideo.dto.GenerateRequest;
import com.volcengine.demo.advideo.dto.GenerationResult.DirectorPlan;
import com.volcengine.demo.advideo.dto.GenerationResult.MarketInsight;
import com.volcengine.demo.advideo.service.PromptService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DirectorAgentTest {

    /**
     * 功能描述：验证分镜时长缺少结构化字段时，仍能从脚本文本中的“时长X秒”提取秒数。
     * 参数解释：无。
     * 返回对象描述：无返回值，断言解析后的分镜秒数。
     * 可能抛出的异常：无。
     */
    @Test
    void shouldParseDurationFromActionTextWhenSecondsFieldMissing() {
        ArkChatClient chatClient = mock(ArkChatClient.class);
        when(chatClient.complete(anyString(), anyString())).thenReturn("""
                {
                  "video_title": "通勤穿搭短片",
                  "shot_list": [
                    {
                      "id": "shot_001",
                      "image": "竖屏近景，女生站在明亮走廊",
                      "action": "固定机位，模特从画面右侧自然走入站定，时长4秒",
                      "reference": "",
                      "words": ""
                    }
                  ]
                }
                """);
        DirectorAgent agent = new DirectorAgent(chatClient, new PromptService(), new ObjectMapper());

        DirectorPlan plan = agent.createPlan(
                new GenerateRequest("生成穿搭广告", "连体裤", "", "", "通勤", "", "15", true, List.of(), List.of()),
                new MarketInsight("商品展示视频", "连体裤", "通勤女性", List.of("省心"), List.of("穿搭"), "突出省心穿搭"),
                "抖音"
        );

        assertThat(plan.scenes()).hasSize(1);
        assertThat(plan.scenes().get(0).seconds()).isEqualTo(4);
    }

    /**
     * 功能描述：验证结构化 seconds 字段优先级高于脚本文本中可能残留的时长描述。
     * 参数解释：无。
     * 返回对象描述：无返回值，断言解析后的分镜秒数。
     * 可能抛出的异常：无。
     */
    @Test
    void shouldPreferStructuredSecondsOverTextDuration() {
        ArkChatClient chatClient = mock(ArkChatClient.class);
        when(chatClient.complete(anyString(), anyString())).thenReturn("""
                {
                  "video_title": "通勤穿搭短片",
                  "shot_list": [
                    {
                      "id": "shot_001",
                      "seconds": 6,
                      "image": "竖屏中景，展示整体穿搭",
                      "action": "轻微推进，时长4秒",
                      "reference": "",
                      "words": ""
                    }
                  ]
                }
                """);
        DirectorAgent agent = new DirectorAgent(chatClient, new PromptService(), new ObjectMapper());

        DirectorPlan plan = agent.createPlan(
                new GenerateRequest("生成穿搭广告", "连体裤", "", "", "通勤", "", "15", true, List.of(), List.of()),
                new MarketInsight("商品展示视频", "连体裤", "通勤女性", List.of("省心"), List.of("穿搭"), "突出省心穿搭"),
                "抖音"
        );

        assertThat(plan.scenes()).hasSize(1);
        assertThat(plan.scenes().get(0).seconds()).isEqualTo(6);
    }
}
