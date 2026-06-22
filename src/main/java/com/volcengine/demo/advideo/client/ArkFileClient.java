package com.volcengine.demo.advideo.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.volcengine.demo.advideo.config.AdVideoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ArkFileClient {

    private static final Logger log = LoggerFactory.getLogger(ArkFileClient.class);

    private final AdVideoProperties properties;
    private final RestClient restClient;

    public ArkFileClient(AdVideoProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.llm().baseUrl()).build();
    }

    public UploadResult uploadVideo(MultipartFile file) {
        return uploadFile(file, "video.mp4");
    }

    public UploadResult uploadImage(MultipartFile file) {
        return uploadFile(file, "image.png");
    }

    private UploadResult uploadFile(MultipartFile file, String fallbackFileName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (!StringUtils.hasText(properties.llm().apiKey())) {
            log.info("Ark file upload api key missing, return mock file id, fileName={}", file.getOriginalFilename());
            return new UploadResult("mock-file-" + System.currentTimeMillis(), valueOrDefault(file.getOriginalFilename(), fallbackFileName));
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("purpose", filePurpose());
        body.add("file", new NamedByteArrayResource(readBytes(file), valueOrDefault(file.getOriginalFilename(), fallbackFileName)));

        try {
            log.info("Upload file to Ark, fileName={}, size={}, purpose={}",
                    file.getOriginalFilename(), file.getSize(), filePurpose());
            FileResponse response = restClient.post()
                    .uri("/files")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header("Authorization", "Bearer " + properties.llm().apiKey())
                    .body(body)
                    .retrieve()
                    .body(FileResponse.class);
            if (response == null || !StringUtils.hasText(response.id())) {
                throw new IllegalStateException("Ark file upload succeeded but file id is empty");
            }
            return new UploadResult(response.id(), valueOrDefault(response.filename(), file.getOriginalFilename()));
        } catch (RestClientResponseException ex) {
            log.error("Ark file upload failed, fileName={}, statusCode={}, responseBody={}",
                    file.getOriginalFilename(), ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Ark file upload failed, fileName={}", file.getOriginalFilename(), ex);
            throw ex;
        }
    }

    private String filePurpose() {
        return "user_data";
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception ex) {
            throw new IllegalStateException("读取上传文件失败", ex);
        }
    }

    private String valueOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    public record UploadResult(String fileId, String fileName) {
    }

    public record FileResponse(
            String id,
            String filename,
            @JsonProperty("bytes") Long bytes
    ) {
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
