package com.volcengine.demo.advideo.service;

import com.volcengine.demo.advideo.config.AdVideoProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class S3StorageServiceTest {

    @Test
    void uploadFinalVideoShouldUseNonExpiringPrefix() throws Exception {
        S3Client client = mock(S3Client.class);
        S3StorageService service = configuredService(client);
        Path video = Files.createTempFile("final-video-", ".mp4");
        Files.writeString(video, "fake mp4", StandardCharsets.UTF_8);

        S3StorageService.UploadResult result = service.uploadFinalVideo(video, "task 001.mp4");

        PutObjectRequest request = putObjectRequest(client);
        assertThat(request.bucket()).isEqualTo("ad-video-gen-java");
        assertThat(request.key()).startsWith("final-videos/");
        assertThat(request.key()).endsWith("/task_001.mp4");
        assertThat(request.contentType()).isEqualTo("video/mp4");
        assertThat(result.fileUrl()).contains("/ad-video-gen-java/final-videos/");
        assertThat(result.fileUrl()).endsWith("/task_001.mp4");
    }

    @Test
    void uploadImageShouldUseExpiringUploadsPrefix() throws Exception {
        S3Client client = mock(S3Client.class);
        S3StorageService service = configuredService(client);
        MockMultipartFile image = new MockMultipartFile(
                "file",
                "hero image.png",
                "image/png",
                "fake image".getBytes(StandardCharsets.UTF_8)
        );

        S3StorageService.UploadResult result = service.uploadImage(image);

        PutObjectRequest request = putObjectRequest(client);
        assertThat(request.bucket()).isEqualTo("ad-video-gen-java");
        assertThat(request.key()).startsWith("uploads/images/");
        assertThat(request.key()).endsWith("-hero_image.png");
        assertThat(request.contentType()).isEqualTo("image/png");
        assertThat(result.fileUrl()).contains("/ad-video-gen-java/uploads/images/");
    }

    // Creates a storage service with S3 settings and an injected mock client.
    private S3StorageService configuredService(S3Client client) {
        S3StorageService service = new S3StorageService(properties());
        ReflectionTestUtils.setField(service, "client", client);
        return service;
    }

    // Captures the PutObject request from the mocked S3 client.
    private PutObjectRequest putObjectRequest(S3Client client) {
        var captor = forClass(PutObjectRequest.class);
        verify(client, times(1)).putObject(captor.capture(), any(RequestBody.class));
        return captor.getValue();
    }

    // Builds the minimal configured properties needed for S3 uploads.
    private AdVideoProperties properties() {
        return new AdVideoProperties(
                null,
                null,
                null,
                new AdVideoProperties.Storage(
                        "http://127.0.0.1:9000",
                        "http://cdn.example.com",
                        "access",
                        "secret",
                        "ad-video-gen-java",
                        "us-east-1",
                        true,
                        true,
                        7
                ),
                new AdVideoProperties.ShortLink("http://localhost:8080"),
                new AdVideoProperties.Ffmpeg("ffmpeg", "./data/final-videos")
        );
    }
}
