package com.verlake.dam.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Properties;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class I18nUtils {

    private static Properties technicalProperties;

    /**
     * Private constructor to hide the implicit public one
     */
    private I18nUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Get MessageSource from Spring context
     */
    private static MessageSource getMessageSource() {
        try {
            return SpringContext.getBean(MessageSource.class);
        } catch (Exception e) {
            log.warn("Could not get MessageSource from Spring context: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get LocaleResolver from Spring context
     */
    private static LocaleResolver getLocaleResolver() {
        try {
            return SpringContext.getBean(LocaleResolver.class);
        } catch (Exception e) {
            log.warn("Could not get LocaleResolver from Spring context: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get a localized message using current locale (from request headers)
     */
    public static String getMessage(String code) {
        MessageSource messageSource = getMessageSource();
        if (messageSource == null) {
            // Fallback to code if messageSource is not available
            return code;
        }
        return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
    }

    /**
     * Get a localized message with arguments using current locale (from request headers)
     */
    public static String getMessage(String code, Object... args) {
        MessageSource messageSource = getMessageSource();
        if (messageSource == null) {
            // Fallback to code if messageSource is not available
            return code;
        }
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    /**
     * Get a localized message for a specific locale
     */
    public static String getMessage(String code, Locale locale) {
        MessageSource messageSource = getMessageSource();
        if (messageSource == null) {
            // Fallback to code if messageSource is not available
            return code;
        }
        return messageSource.getMessage(code, null, locale);
    }

    /**
     * Get a localized message with arguments for a specific locale
     */
    public static String getMessage(String code, Locale locale, Object... args) {
        MessageSource messageSource = getMessageSource();
        if (messageSource == null) {
            // Fallback to code if messageSource is not available
            return code;
        }
        return messageSource.getMessage(code, args, locale);
    }

    /**
     * Get current locale (resolved from request headers)
     */
    public static Locale getCurrentLocale() {
        return LocaleContextHolder.getLocale();
    }

    /**
     * Get locale from request headers
     */
    public static Locale getLocaleFromRequest(HttpServletRequest request) {
        LocaleResolver localeResolver = getLocaleResolver();
        if (localeResolver == null) {
            // Fallback to default locale if localeResolver is not available
            return Locale.ENGLISH;
        }
        return localeResolver.resolveLocale(request);
    }

    /**
     * Get a localized message using locale from request headers
     */
    public static String getMessageFromRequest(String code, HttpServletRequest request) {
        Locale locale = getLocaleFromRequest(request);
        MessageSource messageSource = getMessageSource();
        if (messageSource == null) {
            // Fallback to code if messageSource is not available
            return code;
        }
        return messageSource.getMessage(code, null, locale);
    }

    /**
     * Get a localized message with arguments using locale from request headers
     */
    public static String getMessageFromRequest(String code, HttpServletRequest request, Object... args) {
        Locale locale = getLocaleFromRequest(request);
        MessageSource messageSource = getMessageSource();
        if (messageSource == null) {
            // Fallback to code if messageSource is not available
            return code;
        }
        return messageSource.getMessage(code, args, locale);
    }

    /**
     * Set locale for current thread (for testing or programmatic use)
     */
    public static void setLocale(Locale locale) {
        LocaleContextHolder.setLocale(locale);
    }

    /**
     * Get supported locales
     */
    public static Locale[] getSupportedLocales() {
        return new Locale[]{
            Locale.ENGLISH,  // en
            new Locale("hi") // Hindi
        };
    }

    /**
     * Check if locale is supported
     */
    public static boolean isSupportedLocale(Locale locale) {
        for (Locale supported : getSupportedLocales()) {
            if (supported.equals(locale)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get locale display name in its own language
     */
    public static String getLocaleDisplayName(Locale locale) {
        return locale.getDisplayLanguage(locale);
    }

    /**
     * Get locale display name in current locale
     */
    public static String getLocaleDisplayNameInCurrentLocale(Locale locale) {
        return locale.getDisplayLanguage(getCurrentLocale());
    }

    /**
     * Get supported header names for language preference
     */
    public static String[] getLanguageHeaders() {
        return new String[]{
            "X-Language",           // Custom header
            "Accept-Language",      // Standard HTTP header
            "X-Preferred-Language", // Alternative custom header
            "Language"              // Simple custom header
        };
    }

    /**
     * Get a technical property value
     */
    public static String getTechnicalProperty(String key) {
        if (technicalProperties == null) {
            // Try to load from lang folder if not injected
            loadTechnicalPropertiesFromLangFolder();
        }
        return technicalProperties != null ? technicalProperties.getProperty(key) : null;
    }

    /**
     * Get a technical property value with default
     */
    public static String getTechnicalProperty(String key, String defaultValue) {
        if (technicalProperties == null) {
            // Try to load from lang folder if not injected
            loadTechnicalPropertiesFromLangFolder();
        }
        return technicalProperties != null ? technicalProperties.getProperty(key, defaultValue) : defaultValue;
    }

    /**
     * Get an integer technical property value
     */
    public static int getTechnicalPropertyAsInt(String key) {
        if (technicalProperties == null) {
            // Try to load from lang folder if not injected
            loadTechnicalPropertiesFromLangFolder();
        }
        if (technicalProperties != null) {
            String value = technicalProperties.getProperty(key);
            return value != null ? Integer.parseInt(value) : 0;
        }
        return 0;
    }

    /**
     * Get an integer technical property value with default
     */
    public static int getTechnicalPropertyAsInt(String key, int defaultValue) {
        if (technicalProperties == null) {
            // Try to load from lang folder if not injected
            loadTechnicalPropertiesFromLangFolder();
        }
        if (technicalProperties != null) {
            String value = technicalProperties.getProperty(key);
            return value != null ? Integer.parseInt(value) : defaultValue;
        }
        return defaultValue;
    }

    /**
     * Load technical properties from lang folder if not injected
     */
    private static void loadTechnicalPropertiesFromLangFolder() {
        try {
            Resource resource = new ClassPathResource("lang/technical.properties");
            technicalProperties = PropertiesLoaderUtils.loadProperties(resource);
        } catch (IOException e) {
            // Log error but don't throw exception
            log.warn("Could not load technical.properties from lang folder: {}", e.getMessage());
        }
    }

    /**
     * Get available languages for UI
     */
    public static String getAvailableLanguagesJson() {
        StringBuilder json = new StringBuilder("[");
        Locale[] locales = getSupportedLocales();
        
        for (int i = 0; i < locales.length; i++) {
            Locale locale = locales[i];
            if (i > 0) json.append(",");
            json.append("{");
            json.append("\"code\":\"").append(locale.getLanguage()).append("\",");
            json.append("\"name\":\"").append(getLocaleDisplayName(locale)).append("\",");
            json.append("\"nativeName\":\"").append(getLocaleDisplayName(locale)).append("\"");
            json.append("}");
        }
        json.append("]");
        return json.toString();
    }
} 