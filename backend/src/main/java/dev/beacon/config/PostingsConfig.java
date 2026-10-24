package dev.beacon.config;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(PostingsProperties.class)
public class PostingsConfig {

    /** A fresh builder per job source, so each can set its own base URL and headers. */
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder postingsRestClientBuilder(PostingsProperties properties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent());
    }
}
