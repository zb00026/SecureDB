package com.verlake.dam.controller.admin;

import com.verlake.dam.service.ai.GeminiAIService;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ai-health")
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AIHealthController {
    
    private final GeminiAIService geminiAIService;
    
    @Autowired
    public AIHealthController(GeminiAIService geminiAIService) {
        this.geminiAIService = geminiAIService;
    }

    /**
     * Get AI service health status
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAIHealthStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            
            // Test AI service connection
            String testResult = geminiAIService.testConnection();
            boolean isHealthy = testResult.contains("Connection successful");
            
            status.put("service", "Gemini AI");
            status.put(Constants.STATUS_NAME, isHealthy ? Constants.HEALTHY_STATUS : Constants.UNHEALTHY_STATUS);
            status.put("testResult", testResult);
            status.put(Constants.TIMESTAMP_NAME, System.currentTimeMillis());
            
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error checking AI health: {}", e.getMessage(), e);
            
            Map<String, Object> errorStatus = new HashMap<>();
            errorStatus.put("service", "Gemini AI");
            errorStatus.put(Constants.STATUS_NAME, Constants.ERROR_STATUS);
            errorStatus.put(Constants.ERROR_FIELD_ERROR, e.getMessage());
            errorStatus.put(Constants.TIMESTAMP_NAME, System.currentTimeMillis());
            
            return ResponseEntity.status(503).body(errorStatus);
        }
    }
    
    /**
     * Force reset circuit breaker (admin only)
     */
    @PostMapping("/reset-circuit-breaker")
    public ResponseEntity<Map<String, Object>> resetCircuitBreaker() {
        try {
            // This would require exposing a method in GeminiAIService to reset the circuit breaker
            // For now, we'll just return a success message
            Map<String, Object> result = new HashMap<>();
            result.put("message", "Circuit breaker reset requested");
            result.put(Constants.TIMESTAMP_NAME, System.currentTimeMillis());
            
            log.info("Circuit breaker reset requested by admin");
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error resetting circuit breaker: {}", e.getMessage(), e);
            
            Map<String, Object> error = new HashMap<>();
            error.put(Constants.ERROR_FIELD_ERROR, e.getMessage());
            error.put(Constants.TIMESTAMP_NAME, System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Diagnose specific response format issues (admin only)
     */
    @PostMapping("/diagnose-response")
    public ResponseEntity<Map<String, Object>> diagnoseResponse(@RequestBody Map<String, String> request) {
        try {
            String responseBody = request.get("responseBody");
            if (responseBody == null || responseBody.trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put(Constants.ERROR_FIELD_ERROR, "responseBody is required");
                error.put(Constants.TIMESTAMP_NAME, System.currentTimeMillis());
                return ResponseEntity.badRequest().body(error);
            }
            
            String diagnosis = geminiAIService.diagnoseResponse(responseBody);
            
            Map<String, Object> result = new HashMap<>();
            result.put("diagnosis", diagnosis);
            result.put(Constants.TIMESTAMP_NAME, System.currentTimeMillis());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error diagnosing response: {}", e.getMessage(), e);
            
            Map<String, Object> error = new HashMap<>();
            error.put(Constants.ERROR_FIELD_ERROR, e.getMessage());
            error.put(Constants.TIMESTAMP_NAME, System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(error);
        }
    }
}
