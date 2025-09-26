package com.verlake.dam.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Unix group caching
 */
@Configuration
@EnableCaching
public class UnixGroupCacheConfig {

    /**
     * Cache manager for Unix group operations
     */
    @Bean
    public CacheManager unixGroupCacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        cacheManager.setCacheNames(java.util.Arrays.asList("unixGroups", "unixGroupFolderAccess", "unixGroupSuggestions"));
        return cacheManager;
    }
}
