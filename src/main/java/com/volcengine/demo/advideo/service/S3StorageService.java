package com.volcengine.demo.advideo.service;

import com.volcengine.demo.advideo.config.AdVideoProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketLifecycleConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ExpirationStatus;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.LifecycleExpiration;
import software.amazon.awssdk.services.s3.model.LifecycleRule;
import software.amazon.awssdk.services.s3.model.LifecycleRuleFilter;
import software.amazon.awssdk.services.s3.model.PutBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class S3StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);
    private static final String EXPIRING_UPLOAD_PREFIX = "uploads/";
    private static final String FINAL_VIDEO_PREFIX = "final-videos/";

    private final AdVideoProperties properties;
    private S3Client client;

    public S3StorageService(AdVideoProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        if (!isConfigured()) {
            log.info("S3-compatible storage is not configured, upload API will return mock URLs");
            return;
        }
        AdVideoProperties.Storage storage = storage();
        this.client = S3Client.builder()
                .endpointOverride(URI.create(storage.endpoint()))
                .region(Region.of(valueOrDefault(storage.region(), "us-east-1")))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(storage.accessKey(), storage.secretKey())))
                .forcePathStyle(!Boolean.FALSE.equals(storage.pathStyleAccess()))
                .build();
        ensureBucketReady();
    }

    public UploadResult uploadVideo(MultipartFile file) {
        return upload(file, "videos");
    }

    public UploadResult uploadImage(MultipartFile file) {
        return upload(file, "images");
    }

    /**
     * Uploads composed final videos to a non-expiring prefix.
     */
    public UploadResult uploadFinalVideo(Path file, String fileName) {
        if (file == null) {
            throw new IllegalArgumentException("最终视频文件不能为空");
        }
        String safeFileName = valueOrDefault(fileName, file.getFileName().toString());
        if (!isConfigured()) {
            String mockUrl = "mock://local/final-videos/" + sanitizeFileName(safeFileName);
            log.info("S3-compatible storage not configured, return mock final video url, fileName={}", safeFileName);
            return new UploadResult(safeFileName, mockUrl);
        }

        String objectKey = buildFinalVideoObjectKey(safeFileName);
        try {
            client.putObject(PutObjectRequest.builder()
                            .bucket(bucket())
                            .key(objectKey)
                            .contentType(resolveContentType(null, safeFileName))
                            .build(),
                    RequestBody.fromFile(file));
            String fileUrl = buildPublicUrl(objectKey);
            log.info("Uploaded final video to S3-compatible storage, objectKey={}", objectKey);
            return new UploadResult(safeFileName, fileUrl);
        } catch (Exception ex) {
            throw new IllegalStateException("上传最终视频到 S3 兼容存储失败: " + safeFileName, ex);
        }
    }

    /**
     * Uploads transient source assets to the expiring uploads prefix.
     */
    public UploadResult upload(MultipartFile file, String category) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String fileName = valueOrDefault(file.getOriginalFilename(), category + ".bin");
        if (!isConfigured()) {
            String mockUrl = "mock://local/" + category + "/" + System.currentTimeMillis() + "-" + sanitizeFileName(fileName);
            log.info("S3-compatible storage not configured, return mock url, fileName={}", fileName);
            return new UploadResult(fileName, mockUrl);
        }

        String objectKey = buildObjectKey(category, fileName);
        try (InputStream inputStream = file.getInputStream()) {
            long size = file.getSize() >= 0 ? file.getSize() : -1;
            client.putObject(PutObjectRequest.builder()
                            .bucket(bucket())
                            .key(objectKey)
                            .contentType(resolveContentType(file, fileName))
                            .build(),
                    RequestBody.fromInputStream(inputStream, size));
            String fileUrl = buildPublicUrl(objectKey);
            log.info("Uploaded file to S3-compatible storage, category={}, objectKey={}, size={}, expirationDays={}",
                    category, objectKey, file.getSize(), objectExpirationDays());
            return new UploadResult(fileName, fileUrl);
        } catch (Exception ex) {
            throw new IllegalStateException("上传文件到 S3 兼容存储失败: " + fileName, ex);
        }
    }

    private void ensureBucketReady() {
        try {
            String bucket = bucket();
            boolean exists = bucketExists(bucket);
            if (!exists && Boolean.TRUE.equals(storage().autoCreateBucket())) {
                client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("Created S3 bucket, bucket={}", bucket);
            }
            applyLifecyclePolicyIfSupported(bucket);
        } catch (Exception ex) {
            throw new IllegalStateException("初始化 S3 兼容存储失败", ex);
        }
    }

    private boolean bucketExists(String bucket) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw ex;
        }
    }

    private void applyLifecyclePolicyIfSupported(String bucket) {
        int expirationDays = objectExpirationDays();
        LifecycleRule rule = LifecycleRule.builder()
                .id("expire-uploads-after-" + expirationDays + "-days")
                .status(ExpirationStatus.ENABLED)
                .filter(LifecycleRuleFilter.builder().prefix(EXPIRING_UPLOAD_PREFIX).build())
                .expiration(LifecycleExpiration.builder().days(expirationDays).build())
                .build();
        try {
            client.putBucketLifecycleConfiguration(PutBucketLifecycleConfigurationRequest.builder()
                    .bucket(bucket)
                    .lifecycleConfiguration(BucketLifecycleConfiguration.builder()
                            .rules(List.of(rule))
                            .build())
                    .build());
            log.info("Applied S3 lifecycle policy, bucket={}, prefix={}, expirationDays={}",
                    bucket, EXPIRING_UPLOAD_PREFIX, expirationDays);
        } catch (S3Exception ex) {
            log.warn("S3 lifecycle policy is not applied, bucket={}, prefix={}, expirationDays={}, statusCode={}, message={}",
                    bucket, EXPIRING_UPLOAD_PREFIX, expirationDays, ex.statusCode(), ex.awsErrorDetails().errorMessage());
        }
    }

    private String buildObjectKey(String category, String fileName) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return EXPIRING_UPLOAD_PREFIX + category + "/" + datePath + "/"
                + UUID.randomUUID() + "-" + sanitizeFileName(fileName);
    }

    private String buildFinalVideoObjectKey(String fileName) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return FINAL_VIDEO_PREFIX + datePath + "/" + sanitizeFileName(fileName);
    }

    private String buildPublicUrl(String objectKey) {
        String base = valueOrDefault(storage().publicBaseUrl(), storage().endpoint()).replaceAll("/+$", "");
        String encodedKey = encodeObjectKey(objectKey);
        return base + "/" + bucket() + "/" + encodedKey;
    }

    private String encodeObjectKey(String objectKey) {
        String[] segments = objectKey.split("/");
        StringBuilder encoded = new StringBuilder();
        for (int index = 0; index < segments.length; index += 1) {
            if (index > 0) {
                encoded.append('/');
            }
            encoded.append(URLEncoder.encode(segments[index], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return encoded.toString();
    }

    private String resolveContentType(MultipartFile file, String fileName) {
        if (file != null && StringUtils.hasText(file.getContentType())) {
            return file.getContentType();
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (lower.endsWith(".mov")) {
            return "video/quicktime";
        }
        return "application/octet-stream";
    }

    private String sanitizeFileName(String fileName) {
        String normalized = fileName.replace("\\", "/");
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash >= 0) {
            normalized = normalized.substring(lastSlash + 1);
        }
        normalized = normalized.replaceAll("[^a-zA-Z0-9._-]", "_");
        return StringUtils.hasText(normalized) ? normalized : "file.bin";
    }

    /**
     * Indicates whether object storage has enough settings to use the S3 API.
     */
    public boolean isConfigured() {
        AdVideoProperties.Storage storage = storage();
        return storage != null
                && StringUtils.hasText(storage.endpoint())
                && StringUtils.hasText(storage.accessKey())
                && StringUtils.hasText(storage.secretKey())
                && StringUtils.hasText(storage.bucket());
    }

    private AdVideoProperties.Storage storage() {
        return properties.storage();
    }

    private String bucket() {
        return storage().bucket();
    }

    private int objectExpirationDays() {
        Integer days = storage().objectExpirationDays();
        return days == null || days <= 0 ? 7 : days;
    }

    private String valueOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    public record UploadResult(String fileName, String fileUrl) {
    }
}
