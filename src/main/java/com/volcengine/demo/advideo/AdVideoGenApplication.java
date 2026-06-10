package com.volcengine.demo.advideo;

import com.volcengine.demo.advideo.config.AdVideoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AdVideoProperties.class)
public class AdVideoGenApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdVideoGenApplication.class, args);
    }
}
