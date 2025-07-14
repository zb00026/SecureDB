package com.verlake.dam.configuration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class CustomLocaleResolver implements LocaleResolver {

    private static final List<Locale> SUPPORTED_LOCALES = Arrays.asList(
        Locale.ENGLISH,  // en
        new Locale("hi") // Hindi
    );

    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    // Header names to check for language preference (in order of priority)
    private static final String[] LANGUAGE_HEADERS = {
        "X-Language",           // Custom header
        "Accept-Language",      // Standard HTTP header
        "X-Preferred-Language", // Alternative custom header
        "Language"              // Simple custom header
    };

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        // Check custom headers first
        for (String headerName : LANGUAGE_HEADERS) {
            String languageHeader = request.getHeader(headerName);
            if (languageHeader != null && !languageHeader.trim().isEmpty()) {
                Locale requestedLocale = parseLanguageHeader(languageHeader);
                if (isSupportedLocale(requestedLocale)) {
                    return requestedLocale;
                }
            }
        }

        // Fallback to Accept-Language header (standard HTTP)
        String acceptLanguage = request.getHeader("Accept-Language");
        if (acceptLanguage != null && !acceptLanguage.trim().isEmpty()) {
            Locale requestedLocale = parseAcceptLanguageHeader(acceptLanguage);
            if (isSupportedLocale(requestedLocale)) {
                return requestedLocale;
            }
        }

        // Return default locale if no valid language header found
        return DEFAULT_LOCALE;
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        // This method is called when explicitly setting locale
        // We don't need to do anything here as we're reading from headers
    }

    /**
     * Parse custom language header (e.g., "hi", "en", "hi-IN")
     */
    private Locale parseLanguageHeader(String languageHeader) {
        String language = languageHeader.trim().toLowerCase();
        
        // Handle language codes with country codes (e.g., "hi-IN", "en-US")
        if (language.contains("-")) {
            String[] parts = language.split("-", 2);
            return new Locale(parts[0], parts[1]);
        }
        
        // Handle simple language codes (e.g., "hi", "en")
        return new Locale(language);
    }

    /**
     * Parse standard Accept-Language header (e.g., "hi-IN,hi;q=0.9,en;q=0.8")
     */
    private Locale parseAcceptLanguageHeader(String acceptLanguage) {
        String[] languages = acceptLanguage.split(",");
        
        for (String language : languages) {
            // Remove quality value if present (e.g., "hi;q=0.9" -> "hi")
            String langCode = language.split(";")[0].trim();
            
            if (langCode.contains("-")) {
                String[] parts = langCode.split("-", 2);
                Locale locale = new Locale(parts[0], parts[1]);
                if (isSupportedLocale(locale)) {
                    return locale;
                }
            } else {
                Locale locale = new Locale(langCode);
                if (isSupportedLocale(locale)) {
                    return locale;
                }
            }
        }
        
        return DEFAULT_LOCALE;
    }

    /**
     * Check if the locale is supported
     */
    private boolean isSupportedLocale(Locale locale) {
        return SUPPORTED_LOCALES.stream()
            .anyMatch(supported -> supported.getLanguage().equals(locale.getLanguage()));
    }

    /**
     * Get supported locales
     */
    public static List<Locale> getSupportedLocales() {
        return SUPPORTED_LOCALES;
    }

    /**
     * Get default locale
     */
    public static Locale getDefaultLocale() {
        return DEFAULT_LOCALE;
    }
} 