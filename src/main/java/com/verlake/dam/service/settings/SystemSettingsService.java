package com.verlake.dam.service.settings;

import com.verlake.dam.entity.SystemSettings;
import com.verlake.dam.entity.dto.TimezoneInfoDTO;
import com.verlake.dam.repository.SystemSettingsRepository;
import com.verlake.dam.utils.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemSettingsService {
    
    private final SystemSettingsRepository systemSettingsRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    
    // Timezone API endpoints
    private static final String TIMEZONE_API_BASE = "http://worldtimeapi.org/api/timezone";
    
    // Constants for timezone setting
    public static final String TIMEZONE_SETTING_KEY = Constants.SYSTEM_TIMEZONE_KEY;
    public static final String DEFAULT_TIMEZONE = Constants.DEFAULT_TIMEZONE;
    
    /**
     * Get system timezone setting
     */
    public String getSystemTimezone() {
        return getSettingValue(TIMEZONE_SETTING_KEY, DEFAULT_TIMEZONE);
    }
    
    /**
     * Update system timezone setting
     */
    @Transactional
    public SystemSettings updateTimezone(String timezone) {
        validateTimezone(timezone);
        return updateSetting(TIMEZONE_SETTING_KEY, timezone, "System-wide timezone setting for all timestamps");
    }
    
    /**
     * Get setting value by key with default fallback
     */
    public String getSettingValue(String key, String defaultValue) {
        return systemSettingsRepository.findBySettingKey(key)
                .map(SystemSettings::getSettingValue)
                .orElse(defaultValue);
    }
    
    /**
     * Update or create setting
     */
    @Transactional
    public SystemSettings updateSetting(String key, String value, String description) {
        Optional<SystemSettings> existing = systemSettingsRepository.findBySettingKey(key);
        
        SystemSettings setting = existing.orElse(SystemSettings.builder()
                .settingKey(key)
                .createdAt(LocalDateTime.now())
                .build());
        
        setting.setSettingValue(value);
        setting.setDescription(description);
        setting.setUpdatedAt(LocalDateTime.now());
        
        return systemSettingsRepository.save(setting);
    }
    
    /**
     * Get all system settings
     */
    public List<SystemSettings> getAllSettings() {
        return systemSettingsRepository.findAll();
    }
    
    /**
     * Validate timezone string
     */
    private void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid timezone: " + timezone, e);
        }
    }
    
    /**
     * Convert LocalDateTime to system timezone
     */
    public ZonedDateTime convertToSystemTimezone(LocalDateTime localDateTime) {
        String systemTimezone = getSystemTimezone();
        return localDateTime.atZone(ZoneId.of(systemTimezone));
    }
    
    /**
     * Convert LocalDateTime to system timezone and format as string
     */
    public String formatInSystemTimezone(LocalDateTime localDateTime, String pattern) {
        ZonedDateTime zonedDateTime = convertToSystemTimezone(localDateTime);
        return zonedDateTime.format(DateTimeFormatter.ofPattern(pattern));
    }
    
    /**
     * Get current time in system timezone
     */
    public ZonedDateTime getCurrentTimeInSystemTimezone() {
        return convertToSystemTimezone(LocalDateTime.now());
    }
    
    /**
     * Get list of available timezones from live servers
     */
    public List<TimezoneInfoDTO> getAvailableTimezones() {
        try {
            log.info("Fetching timezones from live servers...");
            
            // Try to fetch from WorldTimeAPI
            List<String> timezoneIds = fetchTimezonesFromWorldTimeAPI();
            
            if (timezoneIds.isEmpty()) {
                log.warn("Failed to fetch from WorldTimeAPI, using common timezones");
                timezoneIds = getCommonTimezones();
            }
            
            // Convert to DTOs
            return timezoneIds.stream()
                    .map(this::createTimezoneInfoDTO)
                    .toList();
                    
        } catch (Exception e) {
            log.error("Failed to fetch timezones: {}", e.getMessage(), e);
            // Fallback to common timezones
            return getCommonTimezones().stream()
                    .map(this::createTimezoneInfoDTO)
                    .toList();
        }
    }
    
    /**
     * Fetch timezone list from WorldTimeAPI with rate limiting
     */
    private List<String> fetchTimezonesFromWorldTimeAPI() {
        try {
            log.info("Fetching timezone list from WorldTimeAPI...");
            ResponseEntity<String[]> response = restTemplate.getForEntity(TIMEZONE_API_BASE, String[].class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<String> timezones = List.of(response.getBody());
                log.info("Successfully fetched {} timezones from WorldTimeAPI", timezones.size());
                return timezones.stream().sorted().toList();
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                log.warn("Rate limit exceeded (429) from WorldTimeAPI");
            } else {
                log.warn("HTTP error {} from WorldTimeAPI: {}", e.getStatusCode().value(), e.getMessage());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch from WorldTimeAPI: {}", e.getMessage());
        }
        return List.of();
    }
    
    /**
     * Get list of common timezones as fallback
     */
    private List<String> getCommonTimezones() {
        return List.of(
            "UTC",
            "Europe/London",
            "Europe/Paris", 
            "Europe/Berlin",
            "Europe/Rome",
            "Europe/Madrid",
            "Europe/Helsinki",
            "Europe/Amsterdam",
            "Europe/Stockholm",
            "Europe/Oslo",
            "Europe/Copenhagen",
            "Europe/Vienna",
            "Europe/Zurich",
            "Europe/Warsaw",
            "Europe/Prague",
            "Europe/Budapest",
            "Europe/Bucharest",
            "Europe/Sofia",
            "Europe/Athens",
            "Europe/Istanbul",
            "Asia/Dubai",
            "Asia/Kolkata",
            "Asia/Dhaka",
            "Asia/Karachi",
            "Asia/Shanghai",
            "Asia/Hong_Kong",
            "Asia/Singapore",
            "Asia/Tokyo",
            "Asia/Seoul",
            "Asia/Bangkok",
            "Asia/Jakarta",
            "Asia/Manila",
            "Asia/Kuala_Lumpur",
            "Asia/Ho_Chi_Minh",
            "Australia/Sydney",
            "Australia/Melbourne",
            "Australia/Brisbane",
            "Australia/Perth",
            "Australia/Adelaide",
            "Pacific/Auckland",
            "America/New_York",
            "America/Chicago",
            "America/Denver",
            "America/Los_Angeles",
            "America/Toronto",
            "America/Vancouver",
            "America/Mexico_City",
            "America/Sao_Paulo",
            "America/Buenos_Aires",
            "America/Lima",
            "America/Bogota",
            "America/Santiago",
            "Africa/Cairo",
            "Africa/Johannesburg",
            "Africa/Lagos",
            "Africa/Nairobi",
            "Africa/Casablanca"
        );
    }
    
    /**
     * Create TimezoneInfoDTO from timezone ID
     */
    private TimezoneInfoDTO createTimezoneInfoDTO(String timezoneId) {
        return TimezoneInfoDTO.builder()
                .id(timezoneId)
                .displayName(timezoneId)
                .utcOffset("")
                .build();
    }
}
