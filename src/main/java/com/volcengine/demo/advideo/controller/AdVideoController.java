package com.volcengine.demo.advideo.controller;

import com.volcengine.demo.advideo.dto.GenerateRequest;
import com.volcengine.demo.advideo.dto.TaskResponse;
import com.volcengine.demo.advideo.service.ShortLinkService;
import com.volcengine.demo.advideo.service.TaskService;
import com.volcengine.demo.advideo.service.UploadService;
import com.volcengine.demo.advideo.service.UploadService.UploadedImage;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping
public class AdVideoController {

    private static final Logger log = LoggerFactory.getLogger(AdVideoController.class);

    private final TaskService taskService;
    private final ShortLinkService shortLinkService;
    private final UploadService uploadService;

    public AdVideoController(TaskService taskService, ShortLinkService shortLinkService, UploadService uploadService) {
        this.taskService = taskService;
        this.shortLinkService = shortLinkService;
        this.uploadService = uploadService;
    }

    @PostMapping("/api/ad-videos")
    public TaskResponse generate(@Valid @RequestBody GenerateRequest request) {
        log.info("Received ad video generation request, productName={}, productUrlPresent={}, referenceImageCount={}",
                request.productName(),
                StringUtils.hasText(request.productUrl()),
                request.referenceImageUrls() == null ? 0 : request.referenceImageUrls().size());
        if (!hasEnoughInput(request)) {
            log.warn("Reject ad video generation request: missing prompt/productUrl/productName/productDescription");
            throw new ResponseStatusException(BAD_REQUEST, "prompt, productUrl, productName or productDescription is required");
        }
        return taskService.submit(request);
    }

    @PostMapping(value = "/api/ad-videos/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TaskResponse generateWithImage(
            @RequestParam(required = false) String prompt,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String productDescription,
            @RequestParam(required = false) String targetAudience,
            @RequestParam(required = false) String sellingPoints,
            @RequestParam(required = false) String style,
            @RequestParam(required = false) String duration,
            @RequestParam(required = false) String landingPageUrl,
            @RequestParam("image") MultipartFile image
    ) {
        UploadedImage uploadedImage;
        try {
            uploadedImage = uploadService.saveImage(image);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
        GenerateRequest request = new GenerateRequest(
                prompt,
                productName,
                productDescription,
                null,
                targetAudience,
                sellingPoints,
                style,
                duration,
                landingPageUrl,
                new ArrayList<>(List.of(uploadedImage.dataUrl()))
        );
        log.info("Received ad video generation upload request, productName={}, imageUrl={}, base64Chars={}",
                productName, uploadedImage.publicUrl(), uploadedImage.dataUrl().length());
        if (!hasEnoughInput(request)) {
            log.warn("Reject upload generation request: missing prompt/productName/productDescription");
            throw new ResponseStatusException(BAD_REQUEST, "prompt, productName or productDescription is required when using uploaded image");
        }
        return taskService.submit(request);
    }

    @GetMapping("/api/ad-videos/{taskId}")
    public ResponseEntity<TaskResponse> getTask(@PathVariable String taskId) {
        log.info("Query ad video task, taskId={}", taskId);
        TaskResponse response = taskService.get(taskId);
        if ("NOT_FOUND".equals(response.status())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/ad-videos/{taskId}/retry")
    public ResponseEntity<TaskResponse> retryTask(@PathVariable String taskId) {
        log.info("Retry ad video task, taskId={}", taskId);
        TaskResponse response = taskService.retry(taskId);
        if ("NOT_FOUND".equals(response.status())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/s/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        return shortLinkService.resolve(code)
                .map(url -> ResponseEntity.status(302).location(URI.create(url)).<Void>build())
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private boolean hasEnoughInput(GenerateRequest request) {
        return StringUtils.hasText(request.prompt())
                || StringUtils.hasText(request.productUrl())
                || StringUtils.hasText(request.productName())
                || StringUtils.hasText(request.productDescription());
    }
}
