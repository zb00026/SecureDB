package com.verlake.dam.controller;

import com.verlake.dam.entity.SystemSettings;
import com.verlake.dam.entity.dto.TimezoneInfoDTO;
import com.verlake.dam.service.settings.SystemSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/settings")
@Slf4j
public class TimezonesController {
    
    @Autowired
    private SystemSettingsService systemSettingsService;

    // Timezone Settings Endpoints
    
    @GetMapping("/timezone")
    public ResponseEntity<?> getTimezone() {
        String timezone = systemSettingsService.getSystemTimezone();
        return ResponseEntity.ok().body(new TimezoneResponse(timezone));
    }
    
    @PostMapping("/timezone")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateTimezone(@RequestBody TimezoneRequest request) {
        log.info("Updating system timezone to: {}", request.getTimezone());
        
        try {
            SystemSettings setting = systemSettingsService.updateTimezone(request.getTimezone());
            return ResponseEntity.ok().body(new TimezoneResponse(setting.getSettingValue()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
    
    @GetMapping("/timezone/available")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TimezoneInfoDTO>> getAvailableTimezones() {
        List<TimezoneInfoDTO> timezones = systemSettingsService.getAvailableTimezones();
        return ResponseEntity.ok(timezones);
    }
    
    // DTOs for timezone requests/responses
    public static class TimezoneRequest {
        private String timezone;
        
        public String getTimezone() { return timezone; }
        public void setTimezone(String timezone) { this.timezone = timezone; }
    }
    
    public static class TimezoneResponse {
        private String timezone;
        
        public TimezoneResponse(String timezone) { this.timezone = timezone; }
        public String getTimezone() { return timezone; }
        public void setTimezone(String timezone) { this.timezone = timezone; }
    }
}
