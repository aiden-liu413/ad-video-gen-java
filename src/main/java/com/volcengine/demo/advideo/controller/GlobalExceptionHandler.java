package com.volcengine.demo.advideo.controller;

import com.volcengine.demo.advideo.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Request validation failed, message={}", message);
        return ResponseEntity.badRequest().body(ApiResponse.failure(valueOrDefault(message, "请求参数不合法")));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Request constraint violation, message={}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.failure(valueOrDefault(ex.getMessage(), "请求参数不合法")));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal request argument, message={}", ex.getMessage(), ex);
        return ResponseEntity.badRequest().body(ApiResponse.failure(valueOrDefault(ex.getMessage(), "请求参数不合法")));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoSuchElementException ex) {
        log.warn("Requested resource not found, message={}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure("资源不存在"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleStaticResourceNotFound(NoResourceFoundException ex) {
        log.debug("Static resource not found, resourcePath={}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure("静态资源不存在"));
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ApiResponse<Void>> handleRestClientResponse(RestClientResponseException ex) {
        log.error("External API request failed, statusCode={}, responseBody={}",
                ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.failure("外部 API 调用失败：" + ex.getStatusCode()));
    }

    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbort(ClientAbortException ex) {
        log.debug("Client aborted response stream, message={}", ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException ex) {
        log.error("Unhandled runtime exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(valueOrDefault(ex.getMessage(), "服务处理失败")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.failure("服务处理失败"));
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
