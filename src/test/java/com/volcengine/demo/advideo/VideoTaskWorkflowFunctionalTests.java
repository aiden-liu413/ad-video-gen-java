package com.volcengine.demo.advideo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.demo.advideo.repository.VideoTaskContextRepository;
import com.volcengine.demo.advideo.repository.VideoTaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class VideoTaskWorkflowFunctionalTests {

    private static final Path TEST_DIR = createTestDir();
    private static final Path FFMPEG_STUB = createFfmpegStub(TEST_DIR);
    private static final Path FINAL_VIDEO_DIR = TEST_DIR.resolve("final-videos");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VideoTaskRepository taskRepository;

    @Autowired
    private VideoTaskContextRepository contextRepository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:video-task-functional;MODE=MySQL;DB_CLOSE_DELAY=-1");
        registry.add("ad-video.llm.api-key", () -> "");
        registry.add("ad-video.image.enabled", () -> "false");
        registry.add("ad-video.video.enabled", () -> "false");
        registry.add("ad-video.short-link.public-base-url", () -> "http://localhost:8080");
        registry.add("ad-video.ffmpeg.binary", () -> FFMPEG_STUB.toString());
        registry.add("ad-video.ffmpeg.output-dir", () -> FINAL_VIDEO_DIR.toString());
    }

    @Test
    void videoTaskWorkflowCanCompleteAndRegenerateFinalCompose() throws Exception {
        String taskId = createTask();

        assertThat(taskRepository.findByTaskId(taskId)).isPresent();
        assertThat(contextRepository.findByTaskId(taskId)).isPresent();

        JsonNode market = advanceAndWait(taskId, "MARKET_PLANNING");
        assertThat(market.path("data").path("status").asText()).isEqualTo("WAITING_REVIEW");

        JsonNode shots = advanceAndWait(taskId, "SHOT_SCRIPT_GENERATING");
        assertThat(shots.path("data").path("shots")).hasSizeGreaterThanOrEqualTo(1);
        mockMvc.perform(post("/api/video-tasks/{taskId}/context", taskId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "shots": %s
                                }
                                """.formatted(shots.path("data").path("shots"))))
                .andExpect(status().isOk());

        JsonNode images = advanceAndWait(taskId, "IMAGE_GENERATING");
        assertThat(images.path("data").path("imageGroups")).hasSizeGreaterThanOrEqualTo(1);
        assertThat(images.path("data").path("scoredImageGroups")).hasSizeGreaterThanOrEqualTo(1);
        mockMvc.perform(post("/api/video-tasks/{taskId}/context", taskId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "scoredImageGroups": %s
                                }
                                """.formatted(images.path("data").path("scoredImageGroups"))))
                .andExpect(status().isOk());

        JsonNode videos = advanceAndWait(taskId, "VIDEO_GENERATING");
        assertThat(videos.path("data").path("videoGroups")).hasSizeGreaterThanOrEqualTo(1);
        assertThat(videos.path("data").path("scoredVideoGroups")).hasSizeGreaterThanOrEqualTo(1);
        mockMvc.perform(post("/api/video-tasks/{taskId}/context", taskId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "scoredVideoGroups": %s
                                }
                                """.formatted(videos.path("data").path("scoredVideoGroups"))))
                .andExpect(status().isOk());

        JsonNode completed = advanceAndWait(taskId, "COMPLETED");
        assertThat(completed.path("data").path("status").asText()).isEqualTo("SUCCESS");
        assertThat(completed.path("data").path("stage").asText()).isEqualTo("COMPLETED");
        assertThat(completed.path("data").path("videoConfig").path("productInfo").path("resources")).hasSize(1);
        assertThat(completed.path("data").path("shots")).hasSizeGreaterThanOrEqualTo(1);
        assertThat(completed.path("data").path("scoredImageGroups")).hasSizeGreaterThanOrEqualTo(1);
        assertThat(completed.path("data").path("scoredVideoGroups")).hasSizeGreaterThanOrEqualTo(1);

        String finalVideoUrl = completed.path("data").path("finalVideo").path("videoUrl").asText();
        assertThat(finalVideoUrl).startsWith("http://localhost:8080/final-videos/");
        assertThat(FINAL_VIDEO_DIR.resolve(taskId + ".mp4")).exists();

        JsonNode selectedImage = completed.path("data").path("selectedImages").get(0);
        JsonNode selectedVideo = completed.path("data").path("selectedVideos").get(0);
        mockMvc.perform(post("/api/video-tasks/{taskId}/select-assets", taskId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "selectedImages": [
                                    { "shotId": "%s", "assetId": "%s" }
                                  ],
                                  "selectedVideos": [
                                    { "shotId": "%s", "assetId": "%s" }
                                  ]
                                }
                                """.formatted(
                                selectedImage.path("shotId").asText(),
                                selectedImage.path("image").path("assetId").asText(),
                                selectedVideo.path("shotId").asText(),
                                selectedVideo.path("video").path("assetId").asText()
                        )))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/video-tasks/{taskId}/regenerate", taskId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "fromStage": "FINAL_COMPOSING",
                                  "reason": "重新合成最终视频"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode regenerated = waitForStage(taskId, "COMPLETED");
        assertThat(regenerated.path("data").path("status").asText()).isEqualTo("SUCCESS");
        assertThat(regenerated.path("data").path("finalVideo").path("videoUrl").asText())
                .isEqualTo(finalVideoUrl);
    }

    private String createTask() throws Exception {
        String response = mockMvc.perform(post("/api/video-tasks")
                        .contentType("application/json")
                        .content("""
                                {
                                  "inputType": "product_image",
                                  "text": "参考上传的商品图片，生成一条 15 秒带货广告视频。商品：玻璃水。卖点：去虫胶、无甲醇、去油膜。",
                                  "imageUrls": [
                                    "data:image/png;base64,iVBORw0KGgo="
                                  ],
                                  "videoType": "商品展示视频",
                                  "platform": "抖音",
                                  "duration": 15,
                                  "aspectRatio": "9:16",
                                  "style": "真实生活方式、明亮、轻快",
                                  "generateImageCount": 1,
                                  "generateVideoCount": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode body = objectMapper.readTree(response);
        assertThat(body.path("code").asInt()).isZero();
        return body.path("data").path("taskId").asText();
    }

    private JsonNode advanceAndWait(String taskId, String expectedStage) throws Exception {
        mockMvc.perform(post("/api/video-tasks/{taskId}/advance", taskId))
                .andExpect(status().isOk());
        return waitForStage(taskId, expectedStage);
    }

    private JsonNode waitForStage(String taskId, String expectedStage) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        JsonNode body = null;
        while (System.nanoTime() < deadline) {
            String response = mockMvc.perform(get("/api/video-tasks/{taskId}", taskId))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
            body = objectMapper.readTree(response);
            String status = body.path("data").path("status").asText();
            String stage = body.path("data").path("stage").asText();
            if ("FAILED".equals(status)) {
                String error = body.path("data").path("errorMessage").asText();
                throw new AssertionError("Task failed: " + error);
            }
            if (expectedStage.equals(stage) && ("WAITING_REVIEW".equals(status) || "SUCCESS".equals(status))) {
                return body;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Timed out waiting for task. Last response: " + body);
    }

    private static Path createTestDir() {
        try {
            return Files.createTempDirectory("ad-video-functional-");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create test directory", ex);
        }
    }

    private static Path createFfmpegStub(Path testDir) {
        try {
            Path script = testDir.resolve("ffmpeg-stub.sh");
            Files.writeString(script, """
                    #!/bin/sh
                    out=""
                    for arg do
                      out="$arg"
                    done
                    printf 'fake mp4 generated by ffmpeg stub\\n' > "$out"
                    """, StandardCharsets.UTF_8);
            script.toFile().setExecutable(true);
            return script;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create ffmpeg stub", ex);
        }
    }
}
