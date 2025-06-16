package com.verlake.dam.exception;

/**
 * Custom exception for license-related operations
 */
public class LicenseException extends RuntimeException {
    
    /**
     * Constructs a new LicenseException with the specified detail message.
     *
     * @param message the detail message
     */
    public LicenseException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new LicenseException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public LicenseException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructs a new LicenseException with the specified cause.
     *
     * @param cause the cause
     */
    public LicenseException(Throwable cause) {
        super(cause);
    }
}