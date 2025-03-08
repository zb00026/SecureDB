package com.verlake.dam.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;


@Configuration
@Slf4j
public class CustomAuthProviderCondition implements BeanPostProcessor {

    @Value("${auth.provider}")
    private String authProvider;

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();

        ConditionalOnAuthProviderParam annotation = AnnotationUtils.findAnnotation(beanClass, ConditionalOnAuthProviderParam.class);
        if (annotation != null) {
            String expectedValue = annotation.containProvider();
            log.info("Checking {} in {}", expectedValue, authProvider);
            if (!authProvider.contains(expectedValue)) {
                // If the condition is not met, return null to prevent the bean from being registered
                return null;
            }
        }
        return bean; // Proceed with the normal bean creation process
    }
}
