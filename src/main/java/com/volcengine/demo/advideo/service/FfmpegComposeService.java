package com.volcengine.demo.advideo.service;

import com.volcengine.demo.advideo.config.AdVideoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class FfmpegComposeService {

    private static final Logger log = LoggerFactory.getLogger(FfmpegComposeService.class);
    private static final Duration TIMEOUT = Duration.ofMinutes(20);

    private final AdVideoProperties properties;
    private final S3StorageService storageService;

    public FfmpegComposeService(AdVideoProperties properties, S3StorageService storageService) {
        this.properties = properties;
        this.storageService = storageService;
    }

    public String compose(String taskId, List<String> videoUrls) {
        List<String> urls = videoUrls == null ? List.of() : videoUrls.stream()
                .filter(StringUtils::hasText)
                .toList();
        if (urls.isEmpty()) {
            throw new IllegalStateException("No selected videos to compose, taskId=" + taskId);
        }

        Path outputDir = Path.of(properties.ffmpeg().outputDir()).toAbsolutePath().normalize();
        Path outputPath = outputDir.resolve(taskId + ".mp4");
        List<Path> tempVideos = new ArrayList<>();
        try {
            Files.createDirectories(outputDir);
            List<String> ffmpegInputs = materializeMockInputs(outputDir, taskId, urls, tempVideos);
            if (ffmpegInputs.size() == 1) {
                run(singleInputCommand(ffmpegInputs.get(0), outputPath), taskId);
            } else {
                Path listFile = writeConcatList(outputDir, taskId, ffmpegInputs);
                try {
                    run(concatCommand(listFile, outputPath), taskId);
                } finally {
                    Files.deleteIfExists(listFile);
                }
            }
            log.info("FFmpeg compose completed, taskId={}, output={}", taskId, outputPath);
            if (!storageService.isConfigured()) {
                return properties.shortLink().publicBaseUrl() + "/final-videos/" + outputPath.getFileName();
            }
            return storageService.uploadFinalVideo(outputPath, outputPath.getFileName().toString()).fileUrl();
        } catch (IOException ex) {
            throw new IllegalStateException("FFmpeg compose failed to prepare local files, taskId=" + taskId, ex);
        } finally {
            for (Path tempVideo : tempVideos) {
                try {
                    Files.deleteIfExists(tempVideo);
                } catch (IOException ex) {
                    log.warn("Failed to delete temporary mock video, taskId={}, file={}", taskId, tempVideo, ex);
                }
            }
        }
    }

    private List<String> materializeMockInputs(Path outputDir, String taskId, List<String> urls, List<Path> tempVideos) throws IOException {
        List<String> inputs = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            if (!url.startsWith("mock://")) {
                inputs.add(url);
                continue;
            }
            Path mockVideo = outputDir.resolve(taskId + "-mock-" + (i + 1) + ".mp4");
            run(mockVideoCommand(mockVideo, i), taskId);
            tempVideos.add(mockVideo);
            inputs.add(mockVideo.toString());
        }
        return inputs;
    }

    private List<String> mockVideoCommand(Path outputPath, int index) {
        List<String> command = baseCommand();
        String color = index % 2 == 0 ? "0x0f766e" : "0x31566f";
        command.addAll(List.of(
                "-f", "lavfi",
                "-i", "color=c=" + color + ":s=720x1280:d=5",
                "-f", "lavfi",
                "-i", "anullsrc=channel_layout=stereo:sample_rate=44100",
                "-shortest",
                "-c:v", "libx264",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                outputPath.toString()
        ));
        return command;
    }

    private List<String> singleInputCommand(String videoUrl, Path outputPath) {
        List<String> command = baseCommand();
        command.addAll(List.of(
                "-reconnect", "1",
                "-reconnect_streamed", "1",
                "-reconnect_delay_max", "2",
                "-i", videoUrl,
                "-c", "copy",
                outputPath.toString()
        ));
        return command;
    }

    private List<String> concatCommand(Path listFile, Path outputPath) {
        List<String> command = baseCommand();
        command.addAll(List.of(
                "-protocol_whitelist", "file,http,https,tcp,tls,crypto",
                "-f", "concat",
                "-safe", "0",
                "-i", listFile.toString(),
                "-c", "copy",
                outputPath.toString()
        ));
        return command;
    }

    private List<String> baseCommand() {
        List<String> command = new ArrayList<>();
        command.add(StringUtils.hasText(properties.ffmpeg().binary()) ? properties.ffmpeg().binary() : "ffmpeg");
        command.add("-y");
        command.add("-hide_banner");
        return command;
    }

    private Path writeConcatList(Path outputDir, String taskId, List<String> videoUrls) throws IOException {
        Path listFile = outputDir.resolve(taskId + ".concat.txt");
        List<String> lines = videoUrls.stream()
                .map(url -> "file '" + escapeConcatPath(url) + "'")
                .toList();
        Files.write(listFile, lines, StandardCharsets.UTF_8);
        return listFile;
    }

    private String escapeConcatPath(String value) {
        return value.replace("'", "'\\''");
    }

    private void run(List<String> command, String taskId) {
        log.info("FFmpeg compose start, taskId={}, command={}", taskId, redactCommand(command));
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Path logFile = null;
        try {
            logFile = Files.createTempFile("ffmpeg-" + taskId + "-", ".log");
            processBuilder.redirectOutput(logFile.toFile());
            Process process = processBuilder.start();
            boolean finished = process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            String output = Files.readString(logFile, StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("FFmpeg compose timeout, taskId=" + taskId);
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("FFmpeg compose failed, taskId=" + taskId + ", exitCode="
                        + process.exitValue() + ", output=" + trimOutput(output));
            }
            log.info("FFmpeg compose output, taskId={}, output={}", taskId, trimOutput(output));
        } catch (IOException ex) {
            throw new IllegalStateException("FFmpeg executable not available, taskId=" + taskId
                    + ", binary=" + properties.ffmpeg().binary(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while composing video, taskId=" + taskId, ex);
        } finally {
            if (logFile != null) {
                try {
                    Files.deleteIfExists(logFile);
                } catch (IOException ex) {
                    log.warn("Failed to delete FFmpeg log file, taskId={}, logFile={}", taskId, logFile, ex);
                }
            }
        }
    }

    private String redactCommand(List<String> command) {
        return String.join(" ", command).replaceAll("X-Tos-[^&\\s]+", "X-Tos-REDACTED");
    }

    private String trimOutput(String output) {
        if (output == null) {
            return "";
        }
        return output.length() <= 2000 ? output : output.substring(output.length() - 2000);
    }
}
