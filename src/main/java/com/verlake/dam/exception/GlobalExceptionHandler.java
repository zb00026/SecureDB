package com.verlake.dam.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    // Handle Unique Constraint Violations
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        Map<String, String> errorResponse = new HashMap<>();
        log.error("A database constraint was violated", ex);
        errorResponse.put("error", "A database constraint was violated");
        errorResponse.put("details", extractConstraintMessage(ex.getMessage())); // Extract meaningful message
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse); // Return 409 Conflict
    }

    // Handle Resource Not Found (e.g., User ID not found)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex, WebRequest request) {
        Map<String, String> errorResponse = new HashMap<>();
        log.error(ex.getReason(), ex);
        errorResponse.put("error", ex.getReason());
        errorResponse.put("status", ex.getStatusCode().toString());
        return ResponseEntity.status(ex.getStatusCode()).body(errorResponse);
    }

    // Handle General Exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGlobalException(Exception ex, WebRequest request) {
        Map<String, String> errorResponse = new HashMap<>();
        log.error("An unexpected error occurred", ex);
        errorResponse.put("error", "An unexpected error occurred");
        errorResponse.put("details", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    // Utility method to extract meaningful error messages
    private String extractConstraintMessage(String fullMessage) {
        if (fullMessage != null && fullMessage.contains("Unique index or primary key violation")) {
            return "A unique constraint was violated. Check fields for duplicates.";
        }
        return "Constraint violation: " + fullMessage;
    }
}