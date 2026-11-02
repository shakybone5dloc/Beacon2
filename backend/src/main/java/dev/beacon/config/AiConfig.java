package dev.beacon.config;

import dev.beacon.application.usage.AiPrices;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiPrices.class)
public class AiConfig {
}
