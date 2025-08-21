package com.verlake.dam.config;

import com.verlake.dam.filter.RequestLoggingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> loggingFilter(RequestLoggingFilter requestLoggingFilter) {
        FilterRegistrationBean<RequestLoggingFilter> registrationBean = new FilterRegistrationBean<>();
        
        registrationBean.setFilter(requestLoggingFilter);
        registrationBean.addUrlPatterns("/*"); // Apply to all URLs
        registrationBean.setName("requestLoggingFilter");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE); // Run this filter first
        
        return registrationBean;
    }
}