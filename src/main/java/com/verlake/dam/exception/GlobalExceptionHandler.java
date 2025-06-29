package com.verlake.dam.exception;

import com.verlake.dam.utils.Constants;
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
        log.error(Constants.ERROR_DATABASE_CONSTRAINT_VIOLATED, ex);
        errorResponse.put(Constants.ERROR_FIELD_ERROR, Constants.ERROR_DATABASE_CONSTRAINT_VIOLATED);
        errorResponse.put(Constants.ERROR_FIELD_DETAILS, extractConstraintMessage(ex.getMessage())); // Extract meaningful message
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse); // Return 409 Conflict
    }

    // Handle Resource Not Found (e.g., User ID not found)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex, WebRequest request) {
        Map<String, String> errorResponse = new HashMap<>();
        log.error(ex.getReason(), ex);
        errorResponse.put(Constants.ERROR_FIELD_ERROR, ex.getReason());
        errorResponse.put(Constants.ERROR_FIELD_STATUS, ex.getStatusCode().toString());
        return ResponseEntity.status(ex.getStatusCode()).body(errorResponse);
    }

    // Handle JWT Token Exceptions
    @ExceptionHandler(JwtTokenException.class)
    public ResponseEntity<Map<String, String>> handleJwtTokenException(JwtTokenException ex, WebRequest request) {
        Map<String, String> errorResponse = new HashMap<>();
        log.error(Constants.ERROR_JWT_TOKEN_ERROR, ex.getMessage(), ex);
        errorResponse.put(Constants.ERROR_FIELD_ERROR, ex.getMessage());
        errorResponse.put(Constants.ERROR_FIELD_TYPE, Constants.ERROR_TYPE_JWT_TOKEN);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    // Handle General Exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGlobalException(Exception ex, WebRequest request) {
        Map<String, String> errorResponse = new HashMap<>();
        log.error(Constants.ERROR_UNEXPECTED_ERROR, ex);
        errorResponse.put(Constants.ERROR_FIELD_ERROR, Constants.ERROR_UNEXPECTED_ERROR);
        errorResponse.put(Constants.ERROR_FIELD_DETAILS, ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    // Utility method to extract meaningful error messages
    private String extractConstraintMessage(String fullMessage) {
        if (fullMessage != null && fullMessage.contains("Unique index or primary key violation")) {
            return Constants.ERROR_UNIQUE_CONSTRAINT_VIOLATED;
        }
        return Constants.ERROR_CONSTRAINT_VIOLATION_PREFIX + fullMessage;
    }
}