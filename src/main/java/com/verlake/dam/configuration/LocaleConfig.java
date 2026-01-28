package com.verlake.dam.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.Properties;

@Configuration
public class LocaleConfig implements WebMvcConfigurer {

    @Bean
    public LocaleResolver localeResolver() {
        return new CustomLocaleResolver();
    }

    @Bean
    public ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("lang/messages", "lang/technical"); // Base names for properties files in lang folder
        source.setDefaultEncoding("UTF-8");
        source.setUseCodeAsDefaultMessage(false);
        source.setFallbackToSystemLocale(false);
        return source;
    }

    @Bean
    public Properties technicalProperties() throws IOException {
        Resource resource = new ClassPathResource("lang/technical.properties");
        return PropertiesLoaderUtils.loadProperties(resource);
    }
} 