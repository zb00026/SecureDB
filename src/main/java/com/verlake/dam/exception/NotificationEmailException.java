package com.verlake.dam.exception;

public class NotificationEmailException extends NotificationJobException {
    
    private final String emailType;
    private final Long taskId;

    public NotificationEmailException(String message, String emailType, Long taskId) {
        super(message);
        this.emailType = emailType;
        this.taskId = taskId;
    }

    public NotificationEmailException(String message, String emailType, Long taskId, Throwable cause) {
        super(message, cause);
        this.emailType = emailType;
        this.taskId = taskId;
    }

    public String getEmailType() {
        return emailType;
    }

    public Long getTaskId() {
        return taskId;
    }
} 