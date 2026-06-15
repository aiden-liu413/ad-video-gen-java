package com.volcengine.demo.advideo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdVideoProperties properties;

    public WebMvcConfig(AdVideoProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String finalVideoLocation = Path.of(properties.ffmpeg().outputDir()).toAbsolutePath().normalize().toUri().toString();
        if (!finalVideoLocation.endsWith("/")) {
            finalVideoLocation = finalVideoLocation + "/";
        }
        registry.addResourceHandler("/final-videos/**")
                .addResourceLocations(finalVideoLocation);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
