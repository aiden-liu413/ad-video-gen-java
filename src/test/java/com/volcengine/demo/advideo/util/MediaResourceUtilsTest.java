package com.volcengine.demo.advideo.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MediaResourceUtilsTest {

    @Test
    void mergeImageResourcesShouldTreatHttpFileIdsAsUrls() {
        List<String> merged = MediaResourceUtils.mergeImageResources(
                List.of("https://example.com/a.jpg"),
                List.of("https://s3.local/bucket/uploads/images/1.jpg", "file_legacy")
        );

        assertThat(merged).containsExactly(
                "https://example.com/a.jpg",
                "https://s3.local/bucket/uploads/images/1.jpg",
                "fileid:file_legacy"
        );
    }

    @Test
    void resolveVideoUrlShouldPreferExplicitUrl() {
        assertThat(MediaResourceUtils.resolveVideoUrl("https://video.example.com/a.mp4", "file_old"))
                .isEqualTo("https://video.example.com/a.mp4");
        assertThat(MediaResourceUtils.resolveVideoUrl("", "https://s3.local/video.mp4"))
                .isEqualTo("https://s3.local/video.mp4");
    }
}
