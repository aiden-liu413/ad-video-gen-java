package com.volcengine.demo.advideo.service;

import com.volcengine.demo.advideo.config.AdVideoProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class UploadService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final AdVideoProperties properties;

    public UploadService(AdVideoProperties properties) {
        this.properties = properties;
    }

    public UploadedImage saveImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("image is required");
        }
        String extension = extension(image.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("only jpg, jpeg, png and webp images are supported");
        }

        Path storageDir = Path.of(properties.upload().storageDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageDir);
            String filename = UUID.randomUUID() + "." + extension;
            Path target = storageDir.resolve(filename).normalize();
            Files.copy(image.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            byte[] bytes = Files.readAllBytes(target);
            String publicUrl = properties.shortLink().publicBaseUrl() + "/uploads/" + filename;
            String dataUrl = "data:" + contentType(extension) + ";base64," + Base64.getEncoder().encodeToString(bytes);
            return new UploadedImage(publicUrl, dataUrl);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to save uploaded image", ex);
        }
    }

    private String extension(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "jpg";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String contentType(String extension) {
        return switch (extension) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    public record UploadedImage(String publicUrl, String dataUrl) {
    }
}
