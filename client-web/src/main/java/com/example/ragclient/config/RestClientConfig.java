package com.example.ragclient.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configurazione RestClient per chiamate API al backend RAG
 */
@Configuration
public class RestClientConfig {

    @Value("${rag.api.base-url}")
    private String ragApiBaseUrl;

    @Value("${rag.api.timeout-seconds:60}")
    private int timeoutSeconds;

    @Bean
    public RestClient ragApiRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutSeconds * 1000);
        factory.setReadTimeout(timeoutSeconds * 1000);
        
        return RestClient.builder()
                .baseUrl(ragApiBaseUrl)
                .requestFactory(factory)
                .build();
    }
}
