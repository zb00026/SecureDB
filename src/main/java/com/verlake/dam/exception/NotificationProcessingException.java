package com.verlake.dam.exception;

public class NotificationProcessingException extends NotificationJobException {
    
    private final Long taskId;
    private final String recipientEmail;

    public NotificationProcessingException(String message, Long taskId, String recipientEmail) {
        super(message);
        this.taskId = taskId;
        this.recipientEmail = recipientEmail;
    }

    public NotificationProcessingException(String message, Long taskId, String recipientEmail, Throwable cause) {
        super(message, cause);
        this.taskId = taskId;
        this.recipientEmail = recipientEmail;
    }

    public Long getTaskId() {
        return taskId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }
} 