package com.verlake.dam.entity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LicenseStatusDTO {
    @JsonProperty("isValid")
    private boolean isValid;
    
    @JsonProperty("isExpiringSoon")
    private boolean isExpiringSoon;
    
    private Date expiryDate;
    private long daysUntilExpiry;
    private String warningMessage;
    
    public static LicenseStatusDTO createExpiryWarning(Date expiryDate, long daysUntilExpiry) {
        LicenseStatusDTO status = new LicenseStatusDTO();
        status.setValid(true);
        status.setExpiringSoon(true);
        status.setExpiryDate(expiryDate);
        status.setDaysUntilExpiry(daysUntilExpiry);
        
        if (daysUntilExpiry <= 1) {
            status.setWarningMessage("Your license expires today! Please contact support to renew your license.");
        } else {
            status.setWarningMessage(String.format("Your license expires in %d days. Please contact support to renew your license.", daysUntilExpiry));
        }
        
        return status;
    }
    
    public static LicenseStatusDTO createValidStatus() {
        LicenseStatusDTO status = new LicenseStatusDTO();
        status.setValid(true);
        status.setExpiringSoon(false);
        return status;
    }
    
    public static LicenseStatusDTO createValidStatusWithExpiry(Date expiryDate, long daysUntilExpiry) {
        LicenseStatusDTO status = new LicenseStatusDTO();
        status.setValid(true);
        status.setExpiringSoon(false);
        status.setExpiryDate(expiryDate);
        status.setDaysUntilExpiry(daysUntilExpiry);
        return status;
    }
    
    public static LicenseStatusDTO createInvalidStatus() {
        LicenseStatusDTO status = new LicenseStatusDTO();
        status.setValid(false);
        status.setExpiringSoon(false);
        status.setWarningMessage("Your license is invalid or has expired. Please contact support.");
        return status;
    }
} 