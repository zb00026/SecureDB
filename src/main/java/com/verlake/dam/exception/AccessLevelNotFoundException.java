package com.verlake.dam.exception;

public class AccessLevelNotFoundException extends RuntimeException {
    public AccessLevelNotFoundException(String message) {
        super(message);
    }
    
    public AccessLevelNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
