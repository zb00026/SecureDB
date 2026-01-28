package com.verlake.dam.entity;

import com.verlake.dam.annotation.Audited;
import com.verlake.dam.enums.EmailType;
import com.verlake.dam.listener.AuditEntityListener;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "emails")
@EntityListeners(AuditEntityListener.class)
@Audited(entity = "EMAIL")
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

    @Column(nullable = false, columnDefinition = "text")
    private String metadata;
    
    @PrePersist
    @PreUpdate
    private void validateMetadata() {
        // Ensure metadata is never null before persisting - use empty JSON string if null
        if (this.metadata == null || this.metadata.trim().isEmpty()) {
            this.metadata = "{}";
        }
    }

    @Column(nullable = false)
    private String subject;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
}
