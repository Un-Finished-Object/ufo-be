package com.ufo.ufo.domain.pattern.config;

import java.util.Random;
import java.util.random.RandomGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PatternRecommendationConfig {

    @Bean
    RandomGenerator patternRecommendationRandom() {
        return new Random();
    }
}
