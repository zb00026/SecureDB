package com.verlake.dam.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class EmailSendingException extends ResponseStatusException {
    public EmailSendingException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public EmailSendingException(HttpStatus status, String reason, Throwable cause) {
        super(status, reason, cause);
    }
}