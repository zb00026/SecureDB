package com.verlake.dam.entity.user;

import jakarta.persistence.*;
import lombok.Data;
import com.verlake.dam.listener.AuditEntityListener;
import com.verlake.dam.annotation.Audited;

import java.time.LocalDateTime;

@Entity
@Table(name = "forgot_passwords")
@Data
@EntityListeners(AuditEntityListener.class)
@Audited(entity = "FORGOT_PASSWORD")
public class ForgotPassword {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(nullable = false)
    private String email;

    @Column(name = "token_code", nullable = false, unique = true)
    private String tokenCode;

    @Column(name = "expiration_date", nullable = false)
    private LocalDateTime expirationDate;

    @Column(name = "sent_date", nullable = false)
    private LocalDateTime sentDate;

    @Column(name = "reset_time")
    private LocalDateTime resetTime;

    @Column(name = "is_used", nullable = false)
    private Boolean isUsed = false;
} 