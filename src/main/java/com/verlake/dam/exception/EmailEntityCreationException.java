package com.verlake.dam.exception;

/**
 * Exception thrown when email entity creation fails
 */
public class EmailEntityCreationException extends RuntimeException {
    
    private final String emailType;
    private final String recipient;
    
    public EmailEntityCreationException(String message) {
        super(message);
        this.emailType = null;
        this.recipient = null;
    }
    
    public EmailEntityCreationException(String message, Throwable cause) {
        super(message, cause);
        this.emailType = null;
        this.recipient = null;
    }
    
    public EmailEntityCreationException(String message, String emailType, String recipient) {
        super(message);
        this.emailType = emailType;
        this.recipient = recipient;
    }
    
    public EmailEntityCreationException(String message, String emailType, String recipient, Throwable cause) {
        super(message, cause);
        this.emailType = emailType;
        this.recipient = recipient;
    }
    
    public String getEmailType() {
        return emailType;
    }
    
    public String getRecipient() {
        return recipient;
    }
} 