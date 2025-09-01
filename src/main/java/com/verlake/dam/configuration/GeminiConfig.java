package com.verlake.dam.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class GeminiConfig {
    
    @Value("${gemini.api.key}")
    private String apiKey;
    
    @Value("${gemini.model.name}")
    private String modelName;
    
    @Value("${gemini.max.tokens}")
    private int maxTokens;
    
    @Value("${gemini.temperature}")
    private float temperature;
    
    @Value("${gemini.top.k}")
    private int topK;
    
    @Value("${gemini.top.p}")
    private float topP;
    
    @Value("${gemini.retry.max-attempts:3}")
    private int maxRetries;
    
    @Value("${gemini.retry.base-delay-ms:1000}")
    private int baseDelayMs;
    
    @Value("${gemini.retry.max-delay-ms:10000}")
    private int maxDelayMs;
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    
    @Bean
    public String geminiApiKey() {
        return apiKey;
    }
    
    @Bean
    public String geminiModelName() {
        return modelName;
    }
    
    @Bean
    public int geminiMaxTokens() {
        return maxTokens;
    }
    
    @Bean
    public float geminiTemperature() {
        return temperature;
    }
    
    @Bean
    public int geminiTopK() {
        return topK;
    }
    
    @Bean
    public float geminiTopP() {
        return topP;
    }
    
    @Bean
    public int geminiMaxRetries() {
        return maxRetries;
    }
    
    @Bean
    public int geminiBaseDelayMs() {
        return baseDelayMs;
    }
    
    @Bean
    public int geminiMaxDelayMs() {
        return maxDelayMs;
    }
} 