package com.volcengine.demo.advideo.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class ProductSourceService {

    private static final Logger log = LoggerFactory.getLogger(ProductSourceService.class);

    private final RestClient restClient;

    public ProductSourceService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public String extractPageSummary(String productUrl) {
        if (!StringUtils.hasText(productUrl)) {
            log.info("Product page url missing, skip page extraction");
            return "";
        }
        try {
            log.info("Fetch product page start, url={}", productUrl);
            String html = restClient.get().uri(productUrl).retrieve().body(String.class);
            if (!StringUtils.hasText(html)) {
                log.warn("Fetch product page returned empty body, url={}", productUrl);
                return "";
            }
            Document document = Jsoup.parse(html);
            String title = document.title();
            String description = document.select("meta[name=description]").attr("content");
            String body = document.body() == null ? "" : document.body().text();
            String summary = (title + "\n" + description + "\n" + body).replaceAll("\\s+", " ").trim();
            String clipped = summary.length() > 1000 ? summary.substring(0, 1000) : summary;
            log.info("Fetch product page done, url={}, summaryChars={}", productUrl, clipped.length());
            return clipped;
        } catch (RuntimeException ex) {
            log.warn("Fetch product page failed, url={}, error={}", productUrl, ex.getMessage());
            return "商品链接读取失败：" + ex.getMessage();
        }
    }
}
