package com.verlake.dam.entity.assets.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a ping operation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PingResult {
    private boolean success;
    private String message;
    private long responseTimeMs;

    public static PingResult success(String message, long responseTimeMs) {
        return new PingResult(true, message, responseTimeMs);
    }

    public static PingResult failure(String message) {
        return new PingResult(false, message, 0);
    }

    public static PingResult failure(String message, long responseTimeMs) {
        return new PingResult(false, message, responseTimeMs);
    }

    @Override
    public String toString() {
        return String.format("PingResult{success=%s, message='%s', responseTime=%dms}", 
                           success, message, responseTimeMs);
    }
}
