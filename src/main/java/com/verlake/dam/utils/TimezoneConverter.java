package com.verlake.dam.utils;

import com.verlake.dam.service.settings.SystemSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Slf4j
public class TimezoneConverter {
    
    @Autowired
    private SystemSettingsService systemSettingsService;
    
    /**
     * Convert LocalDateTime to system timezone string
     */
    public String convertToSystemTimezoneString(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        
        try {
            String systemTimezone = systemSettingsService.getSystemTimezone();
            // Treat persisted timestamps as UTC and convert to configured system timezone
            ZonedDateTime utcDateTime = localDateTime.atZone(ZoneId.of(Constants.DEFAULT_TIMEZONE));
            ZonedDateTime systemZonedDateTime = utcDateTime.withZoneSameInstant(ZoneId.of(systemTimezone));
            return systemZonedDateTime.format(DateTimeFormatter.ofPattern(Constants.TIMEZONE_DISPLAY_PATTERN));
        } catch (Exception e) {
            log.warn("Failed to convert timestamp to system timezone: {}", e.getMessage());
            return localDateTime.toString(); // Fallback to original
        }
    }
    
    /**
     * Convert LocalDateTime to system timezone string with custom pattern
     */
    public String convertToSystemTimezoneString(LocalDateTime localDateTime, String pattern) {
        if (localDateTime == null) {
            return null;
        }
        
        try {
            String systemTimezone = systemSettingsService.getSystemTimezone();
            ZonedDateTime utcDateTime = localDateTime.atZone(ZoneId.of(Constants.DEFAULT_TIMEZONE));
            ZonedDateTime systemZonedDateTime = utcDateTime.withZoneSameInstant(ZoneId.of(systemTimezone));
            return systemZonedDateTime.format(DateTimeFormatter.ofPattern(pattern));
        } catch (Exception e) {
            log.warn("Failed to convert timestamp to system timezone: {}", e.getMessage());
            return localDateTime.toString(); // Fallback to original
        }
    }
    
    /**
     * Convert list of LocalDateTime objects to system timezone strings
     */
    public List<String> convertToSystemTimezoneStrings(List<LocalDateTime> localDateTimes) {
        if (localDateTimes == null) {
            return null;
        }
        
        return localDateTimes.stream()
                .map(this::convertToSystemTimezoneString)
                .toList();
    }
    
    /**
     * Get current time in system timezone as string
     */
    public String getCurrentTimeInSystemTimezone() {
        // Use current UTC time and convert to system timezone
        return convertToSystemTimezoneString(LocalDateTime.now());
    }
    
    /**
     * Get system timezone identifier
     */
    public String getSystemTimezone() {
        return systemSettingsService.getSystemTimezone();
    }
}
