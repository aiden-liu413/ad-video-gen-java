package com.volcengine.demo.advideo.service;

import com.volcengine.demo.advideo.config.AdVideoProperties;
import com.volcengine.demo.advideo.entity.ShortLinkEntity;
import com.volcengine.demo.advideo.repository.ShortLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class ShortLinkService {

    private static final Logger log = LoggerFactory.getLogger(ShortLinkService.class);

    private final AdVideoProperties properties;
    private final ShortLinkRepository shortLinkRepository;

    public ShortLinkService(AdVideoProperties properties, ShortLinkRepository shortLinkRepository) {
        this.properties = properties;
        this.shortLinkRepository = shortLinkRepository;
    }

    public String createShortLink(String originalUrl) {
        String code = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(originalUrl.getBytes(StandardCharsets.UTF_8));
        code = code.length() > 10 ? code.substring(0, 10) : code;
        ShortLinkEntity entity = new ShortLinkEntity();
        entity.setCode(code);
        entity.setOriginalUrl(originalUrl);
        entity.setCreatedAt(Instant.now());
        shortLinkRepository.save(entity);
        String shortLink = properties.shortLink().publicBaseUrl() + "/s/" + code;
        log.info("Create short link, code={}, originalUrl={}", code, originalUrl);
        return shortLink;
    }

    public Optional<String> resolve(String code) {
        Optional<String> originalUrl = shortLinkRepository.findById(code).map(ShortLinkEntity::getOriginalUrl);
        log.info("Resolve short link, code={}, found={}", code, originalUrl.isPresent());
        return originalUrl;
    }
}
