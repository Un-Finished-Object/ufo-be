package com.ufo.ufo.support.fixture;

import com.ufo.ufo.domain.image.config.ImageProperties;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class ProfileImageTestConfig {

    @Bean
    ImageProperties imageProperties() {
        return new ImageProperties(5, 10_485_760L, List.of("image/jpeg"),
                "https://cdn.example.test", "defaults/profile.png",
                new ImageProperties.S3("test-bucket", "ap-northeast-2", 5, "https://s3.example.test"));
    }
}
