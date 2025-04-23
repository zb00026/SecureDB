package com.verlake.dam.exception;

public class NotificationTimeoutException extends NotificationJobException {
    
    private final long timeoutMs;

    public NotificationTimeoutException(String message, long timeoutMs) {
        super(message);
        this.timeoutMs = timeoutMs;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }
} 