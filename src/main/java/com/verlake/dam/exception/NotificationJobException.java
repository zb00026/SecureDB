package com.verlake.dam.exception;

public class NotificationJobException extends RuntimeException {
    
    public NotificationJobException(String message) {
        super(message);
    }

    public NotificationJobException(String message, Throwable cause) {
        super(message, cause);
    }
} 