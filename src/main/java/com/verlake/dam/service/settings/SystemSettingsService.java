package com.verlake.dam.service.settings;

import com.verlake.dam.entity.SystemSettings;
import com.verlake.dam.entity.dto.TimezoneInfoDTO;
import com.verlake.dam.repository.SystemSettingsRepository;
import com.verlake.dam.utils.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    
    // Constants for AWS Secrets Manager setting
    public static final String AWS_SECRETS_MANAGER_ENABLED_KEY = Constants.AWS_SECRETS_MANAGER_ENABLED_KEY;
    public static final String AWS_SECRETS_MANAGER_ENABLED_DEFAULT = Constants.AWS_SECRETS_MANAGER_ENABLED_DEFAULT;
    
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
     * Get AWS Secrets Manager enabled setting
     */
    public boolean isAWSSecretsManagerEnabled() {
        return Boolean.parseBoolean(getSettingValue(AWS_SECRETS_MANAGER_ENABLED_KEY, AWS_SECRETS_MANAGER_ENABLED_DEFAULT));
    }
    
    /**
     * Update AWS Secrets Manager enabled setting
     */
    @Transactional
    public SystemSettings updateAWSSecretsManagerEnabled(boolean enabled) {
        return updateSetting(AWS_SECRETS_MANAGER_ENABLED_KEY, String.valueOf(enabled), 
                "Enable/disable AWS Secrets Manager integration for asset credentials");
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
            
            // Convert to DTOs and sort by continent, then country, then timezone ID
            return timezoneIds.stream()
                    .map(this::createTimezoneInfoDTO)
                    .sorted(this::compareTimezones)
                    .toList();
                    
        } catch (Exception e) {
            log.error("Failed to fetch timezones: {}", e.getMessage(), e);
            // Fallback to common timezones
            return getCommonTimezones().stream()
                    .map(this::createTimezoneInfoDTO)
                    .sorted(this::compareTimezones)
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
        String countryName = getCountryName(timezoneId);
        String utcOffset = getUtcOffsetString(timezoneId);
        String cityName = getCityName(timezoneId);
        String displayName = utcOffset + " " + countryName + "(" + cityName + ")";
        
        return TimezoneInfoDTO.builder()
                .id(timezoneId)
                .displayName(displayName)
                .utcOffset(utcOffset)
                .build();
    }
    
    /**
     * Get city name from timezone ID
     * Example: "Asia/Shanghai" -> "Shanghai", "Europe/London" -> "London"
     */
    private String getCityName(String timezoneId) {
        if (timezoneId == null || timezoneId.isEmpty()) {
            return "";
        }
        
        if ("UTC".equals(timezoneId)) {
            return "UTC";
        }
        
        if (timezoneId.contains("/")) {
            String[] parts = timezoneId.split("/");
            String city = parts[parts.length - 1];
            // Replace underscores with spaces and capitalize
            city = city.replace("_", " ");
            return capitalizeWords(city);
        }
        
        return timezoneId;
    }
    
    /**
     * Get UTC offset string in format "(UTC+08:00)" or "(UTC-05:00)"
     */
    private String getUtcOffsetString(String timezoneId) {
        try {
            if (timezoneId == null || timezoneId.isEmpty()) {
                return Constants.TIMEZONE_UTC_OFFSET_DEFAULT;
            }
            
            if ("UTC".equals(timezoneId)) {
                return Constants.TIMEZONE_UTC_OFFSET_DEFAULT;
            }
            
            ZoneId zoneId = ZoneId.of(timezoneId);
            ZoneOffset offset = zoneId.getRules().getOffset(Instant.now());
            
            int totalSeconds = offset.getTotalSeconds();
            int hours = totalSeconds / 3600;
            int minutes = Math.abs((totalSeconds % 3600) / 60);
            
            String sign = hours >= 0 ? "+" : "-";
            String hoursStr = String.format("%02d", Math.abs(hours));
            String minutesStr = String.format("%02d", minutes);
            
            return String.format("(UTC%s%s:%s)", sign, hoursStr, minutesStr);
        } catch (Exception e) {
            log.warn("Failed to get UTC offset for timezone {}: {}", timezoneId, e.getMessage());
            return Constants.TIMEZONE_UTC_OFFSET_DEFAULT;
        }
    }
    
    /**
     * Compare timezones for sorting: by UTC offset, then country, then timezone ID
     */
    private int compareTimezones(TimezoneInfoDTO t1, TimezoneInfoDTO t2) {
        // Compare by UTC offset (in total seconds)
        int offset1 = getUtcOffsetSeconds(t1.getId());
        int offset2 = getUtcOffsetSeconds(t2.getId());
        
        int offsetCompare = Integer.compare(offset1, offset2);
        if (offsetCompare != 0) {
            return offsetCompare;
        }
        
        // If same UTC offset, compare by country name
        String country1 = getCountryName(t1.getId());
        String country2 = getCountryName(t2.getId());
        int countryCompare = country1.compareToIgnoreCase(country2);
        if (countryCompare != 0) {
            return countryCompare;
        }
        
        // If same country, sort by timezone ID
        return t1.getId().compareToIgnoreCase(t2.getId());
    }
    
    /**
     * Get UTC offset in total seconds for sorting
     */
    private int getUtcOffsetSeconds(String timezoneId) {
        try {
            if (timezoneId == null || timezoneId.isEmpty() || "UTC".equals(timezoneId)) {
                return 0;
            }
            
            ZoneId zoneId = ZoneId.of(timezoneId);
            ZoneOffset offset = zoneId.getRules().getOffset(Instant.now());
            return offset.getTotalSeconds();
        } catch (Exception e) {
            log.warn("Failed to get UTC offset seconds for timezone {}: {}", timezoneId, e.getMessage());
            return 0;
        }
    }
    
    /**
     * Get country name from timezone ID
     * Returns country name if mapped, otherwise returns a formatted version of the timezone ID
     */
    private String getCountryName(String timezoneId) {
        if (timezoneId == null || timezoneId.isEmpty()) {
            return "Unknown";
        }
        
        // Check if it's UTC
        if ("UTC".equals(timezoneId)) {
            return "UTC";
        }
        
        // Get country name from mapping
        String countryName = TIMEZONE_TO_COUNTRY_MAP.get(timezoneId);
        if (countryName != null) {
            return countryName;
        }
        
        // Try to infer from timezone ID pattern
        // For example: "Asia/Kolkata" -> "India", "Europe/London" -> "United Kingdom"
        String[] parts = timezoneId.split("/");
        if (parts.length >= 2) {
            String city = parts[parts.length - 1];
            // Try to find a more specific mapping
            countryName = TIMEZONE_TO_COUNTRY_MAP.get(city);
            if (countryName != null) {
                return countryName;
            }
        }
        
        // Fallback: return formatted timezone ID
        return formatTimezoneIdAsCountry(timezoneId);
    }
    
    /**
     * Format timezone ID as a readable country/region name
     */
    private String formatTimezoneIdAsCountry(String timezoneId) {
        if (timezoneId.contains("/")) {
            String[] parts = timezoneId.split("/");
            String city = parts[parts.length - 1];
            
            // Replace underscores with spaces and capitalize
            city = city.replace("_", " ");
            city = capitalizeWords(city);
            
            return city;
        }
        return timezoneId;
    }
    
    /**
     * Capitalize words in a string
     */
    private String capitalizeWords(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        String[] words = str.split(" ");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            if (!words[i].isEmpty()) {
                result.append(Character.toUpperCase(words[i].charAt(0)));
                if (words[i].length() > 1) {
                    result.append(words[i].substring(1).toLowerCase());
                }
            }
        }
        return result.toString();
    }
    
    /**
     * Mapping of timezone IDs to country names
     */
    private static final Map<String, String> TIMEZONE_TO_COUNTRY_MAP = new HashMap<>();
    
    static {
        // UTC
        TIMEZONE_TO_COUNTRY_MAP.put("UTC", "UTC");
        
        // Europe
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/London", "United Kingdom");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Paris", "France");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Berlin", "Germany");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Rome", "Italy");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Madrid", "Spain");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Helsinki", "Finland");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Amsterdam", "Netherlands");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Stockholm", "Sweden");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Oslo", "Norway");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Copenhagen", "Denmark");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Vienna", "Austria");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Zurich", "Switzerland");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Warsaw", "Poland");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Prague", "Czech Republic");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Budapest", "Hungary");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Bucharest", "Romania");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Sofia", "Bulgaria");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Athens", "Greece");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Istanbul", "Turkey");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Dublin", "Ireland");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Lisbon", "Portugal");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Brussels", "Belgium");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Luxembourg", "Luxembourg");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Moscow", "Russia");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Kiev", "Ukraine");
        TIMEZONE_TO_COUNTRY_MAP.put("Europe/Minsk", "Belarus");
        
        // Asia
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Dubai", "United Arab Emirates");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Kolkata", "India");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Dhaka", "Bangladesh");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Karachi", "Pakistan");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Shanghai", "China");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Hong_Kong", "Hong Kong");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Singapore", "Singapore");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Tokyo", "Japan");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Seoul", "South Korea");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Bangkok", "Thailand");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Jakarta", "Indonesia");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Manila", "Philippines");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Kuala_Lumpur", "Malaysia");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Ho_Chi_Minh", "Vietnam");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Taipei", "Taiwan");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Colombo", "Sri Lanka");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Kathmandu", "Nepal");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Dushanbe", "Tajikistan");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Famagusta", "Cyprus");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Gaza", "Palestine");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Harbin", "China");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Hebron", "Palestine");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Tehran", "Iran");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Baghdad", "Iraq");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Riyadh", "Saudi Arabia");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Jerusalem", "Israel");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Beirut", "Lebanon");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Amman", "Jordan");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Damascus", "Syria");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Baku", "Azerbaijan");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Yerevan", "Armenia");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Tbilisi", "Georgia");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Almaty", "Kazakhstan");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Tashkent", "Uzbekistan");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Bishkek", "Kyrgyzstan");
        TIMEZONE_TO_COUNTRY_MAP.put("Asia/Ulaanbaatar", "Mongolia");
        
        // Australia & Pacific
        TIMEZONE_TO_COUNTRY_MAP.put("Australia/Sydney", Constants.TIMEZONE_COUNTRY_AUSTRALIA);
        TIMEZONE_TO_COUNTRY_MAP.put("Australia/Melbourne", Constants.TIMEZONE_COUNTRY_AUSTRALIA);
        TIMEZONE_TO_COUNTRY_MAP.put("Australia/Brisbane", Constants.TIMEZONE_COUNTRY_AUSTRALIA);
        TIMEZONE_TO_COUNTRY_MAP.put("Australia/Perth", Constants.TIMEZONE_COUNTRY_AUSTRALIA);
        TIMEZONE_TO_COUNTRY_MAP.put("Australia/Adelaide", Constants.TIMEZONE_COUNTRY_AUSTRALIA);
        TIMEZONE_TO_COUNTRY_MAP.put("Australia/Darwin", Constants.TIMEZONE_COUNTRY_AUSTRALIA);
        TIMEZONE_TO_COUNTRY_MAP.put("Pacific/Auckland", "New Zealand");
        TIMEZONE_TO_COUNTRY_MAP.put("Pacific/Honolulu", Constants.TIMEZONE_COUNTRY_UNITED_STATES);
        TIMEZONE_TO_COUNTRY_MAP.put("Pacific/Fiji", "Fiji");
        TIMEZONE_TO_COUNTRY_MAP.put("Pacific/Guam", "Guam");
        
        // Americas
        TIMEZONE_TO_COUNTRY_MAP.put("America/New_York", Constants.TIMEZONE_COUNTRY_UNITED_STATES);
        TIMEZONE_TO_COUNTRY_MAP.put("America/Chicago", Constants.TIMEZONE_COUNTRY_UNITED_STATES);
        TIMEZONE_TO_COUNTRY_MAP.put("America/Denver", Constants.TIMEZONE_COUNTRY_UNITED_STATES);
        TIMEZONE_TO_COUNTRY_MAP.put("America/Los_Angeles", Constants.TIMEZONE_COUNTRY_UNITED_STATES);
        TIMEZONE_TO_COUNTRY_MAP.put("America/Phoenix", Constants.TIMEZONE_COUNTRY_UNITED_STATES);
        TIMEZONE_TO_COUNTRY_MAP.put("America/Anchorage", Constants.TIMEZONE_COUNTRY_UNITED_STATES);
        TIMEZONE_TO_COUNTRY_MAP.put("America/Toronto", Constants.TIMEZONE_COUNTRY_CANADA);
        TIMEZONE_TO_COUNTRY_MAP.put("America/Vancouver", Constants.TIMEZONE_COUNTRY_CANADA);
        TIMEZONE_TO_COUNTRY_MAP.put("America/Montreal", Constants.TIMEZONE_COUNTRY_CANADA);
        TIMEZONE_TO_COUNTRY_MAP.put("America/Mexico_City", "Mexico");
        TIMEZONE_TO_COUNTRY_MAP.put("America/Sao_Paulo", "Brazil");
        TIMEZONE_TO_COUNTRY_MAP.put("America/Buenos_Aires", "Argentina");
        TIMEZONE_TO_COUNTRY_MAP.put("America/Lima", "Peru");
        TIMEZONE_TO_COUNTRY_MAP.put("America/Bogota", "Colombia");
        TIMEZONE_TO_COUNTRY_MAP.put("America/Santiago", "Chile");
        TIMEZONE_TO_COUNTRY_MAP.put("America/Caracas", "Venezuela");
        TIMEZONE_TO_COUNTRY_MAP.put("America/Montevideo", "Uruguay");
        TIMEZONE_TO_COUNTRY_MAP.put("America/La_Paz", "Bolivia");
        TIMEZONE_TO_COUNTRY_MAP.put("America/Asuncion", "Paraguay");
        TIMEZONE_TO_COUNTRY_MAP.put("America/Guayaquil", "Ecuador");
        TIMEZONE_TO_COUNTRY_MAP.put("America/Panama", "Panama");
        TIMEZONE_TO_COUNTRY_MAP.put("America/Costa_Rica", "Costa Rica");
        TIMEZONE_TO_COUNTRY_MAP.put("America/Guatemala", "Guatemala");
        TIMEZONE_TO_COUNTRY_MAP.put("America/Havana", "Cuba");
        TIMEZONE_TO_COUNTRY_MAP.put("America/Jamaica", "Jamaica");
        
        // Africa
        TIMEZONE_TO_COUNTRY_MAP.put("Africa/Cairo", "Egypt");
        TIMEZONE_TO_COUNTRY_MAP.put("Africa/Johannesburg", "South Africa");
        TIMEZONE_TO_COUNTRY_MAP.put("Africa/Lagos", "Nigeria");
        TIMEZONE_TO_COUNTRY_MAP.put("Africa/Nairobi", "Kenya");
        TIMEZONE_TO_COUNTRY_MAP.put("Africa/Casablanca", "Morocco");
        TIMEZONE_TO_COUNTRY_MAP.put("Africa/Tunis", "Tunisia");
        TIMEZONE_TO_COUNTRY_MAP.put("Africa/Algiers", "Algeria");
        TIMEZONE_TO_COUNTRY_MAP.put("Africa/Addis_Ababa", "Ethiopia");
        TIMEZONE_TO_COUNTRY_MAP.put("Africa/Dar_es_Salaam", "Tanzania");
        TIMEZONE_TO_COUNTRY_MAP.put("Africa/Kampala", "Uganda");
        TIMEZONE_TO_COUNTRY_MAP.put("Africa/Accra", "Ghana");
    }
}
