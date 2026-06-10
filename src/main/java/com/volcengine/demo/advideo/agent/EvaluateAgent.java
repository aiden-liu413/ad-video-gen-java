package com.volcengine.demo.advideo.agent;

import com.volcengine.demo.advideo.client.ArkChatClient;
import com.volcengine.demo.advideo.dto.GenerationResult.DirectorPlan;
import com.volcengine.demo.advideo.dto.GenerationResult.Evaluation;
import com.volcengine.demo.advideo.dto.GenerationResult.MultimediaResult;
import com.volcengine.demo.advideo.dto.GenerationResult.Scene;
import com.volcengine.demo.advideo.service.PromptService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

@Service
public class EvaluateAgent {

    private final ArkChatClient chatClient;
    private final PromptService promptService;

    public EvaluateAgent(ArkChatClient chatClient, PromptService promptService) {
        this.chatClient = chatClient;
        this.promptService = promptService;
    }

    public Evaluation evaluate(DirectorPlan plan, MultimediaResult multimedia) {
        String suggestion = chatClient.complete(
                promptService.evaluateAgent(),
                "请根据如下已生成的分镜图片和视频结果，评估素材质量并给出优化建议：\n\n"
                        + "分镜脚本：\n"
                        + plan.script()
                        + "\n\n生成素材：\n"
                        + mediaSummary(plan, multimedia)
        );
        return new Evaluation(
                86,
                List.of("已在图片/视频生成后进行素材评估", "评估输入包含分镜、图片地址和视频地址"),
                List.of("当前 Java 版本尚未实现原项目的多候选抽卡筛选", "真实评分需以 evaluate-agent 返回内容为准"),
                suggestion
        );
    }

    private String mediaSummary(DirectorPlan plan, MultimediaResult multimedia) {
        StringBuilder builder = new StringBuilder();
        List<Scene> scenes = plan.scenes();
        List<String> imageUrls = multimedia.seedreamImageUrls();
        IntStream.range(0, scenes.size()).forEach(index -> {
            Scene scene = scenes.get(index);
            builder.append("shot_").append(scene.index()).append("\n")
                    .append("image_prompt: ").append(scene.visualPrompt()).append("\n")
                    .append("action: ").append(scene.narration()).append("\n")
                    .append("words: ").append(scene.caption()).append("\n")
                    .append("image_url: ").append(index < imageUrls.size() ? imageUrls.get(index) : "").append("\n\n");
        });
        builder.append("video_task_id: ").append(multimedia.seedanceTaskId()).append("\n")
                .append("video_url: ").append(multimedia.videoUrl()).append("\n");
        return builder.toString();
    }
}
