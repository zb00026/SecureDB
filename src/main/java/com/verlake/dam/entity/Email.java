package com.verlake.dam.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.verlake.dam.converter.JsonAttributeConverter;
import com.verlake.dam.enums.EmailType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "emails")
@Data
public class Email {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email_to", nullable = false)
    private String emailTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_type", nullable = false)
    private EmailType emailType;

    @Column(nullable = false, columnDefinition = "json")
    @Convert(converter = JsonAttributeConverter.class)
    private JsonNode metadata;

    @Column(nullable = false)
    private String subject;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
}
