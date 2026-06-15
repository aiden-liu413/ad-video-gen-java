package com.volcengine.demo.advideo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:context-loads;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "ad-video.llm.api-key=",
        "ad-video.image.enabled=false",
        "ad-video.video.enabled=false"
})
class AdVideoGenApplicationTests {

    @Test
    void contextLoads() {
    }
}
