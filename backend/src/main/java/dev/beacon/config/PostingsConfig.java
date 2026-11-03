package dev.beacon.config;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(PostingsProperties.class)
public class PostingsConfig {

    /** For sources that run a live Google search on each call (Google Jobs, JSearch). */
    public static final String LIVE_SEARCH = "liveSearchRestClientBuilder";

    /** A fresh builder per job source, so each can set its own base URL and headers. */
    @Bean
    @Primary
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder postingsRestClientBuilder(PostingsProperties properties) {
        return builder(properties, Duration.ofSeconds(10));
    }

    /**
     * A live Google search can take well over ten seconds, especially the first time a
     * query is asked, so these sources wait longer before giving up.
     */
    @Bean(LIVE_SEARCH)
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder liveSearchRestClientBuilder(PostingsProperties properties) {
        return builder(properties, Duration.ofSeconds(30));
    }

    private static RestClient.Builder builder(PostingsProperties properties, Duration readTimeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent());
    }
}
