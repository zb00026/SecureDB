package com.verlake.dam.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Jackson configuration to handle Hibernate proxies and prevent serialization issues
 */
@Configuration
public class JacksonConfiguration {

    @Autowired
    private HibernateProxyModule hibernateProxyModule;

    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();
        
        // Configure to handle Hibernate proxies
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        
        // Register JavaTimeModule for LocalDateTime serialization
        objectMapper.registerModule(new JavaTimeModule());
        
        // Register custom Hibernate proxy module
        objectMapper.registerModule(hibernateProxyModule);
        
        // Disable serialization of dates as timestamps
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        
        return objectMapper;
    }
} 