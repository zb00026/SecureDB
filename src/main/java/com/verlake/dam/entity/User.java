package com.verlake.dam.entity;


import jakarta.persistence.*;
import lombok.Data;
import com.verlake.dam.listener.AuditEntityListener;
import com.verlake.dam.annotation.Audited;

import java.util.*;

@Entity
@Table(name = "users")
@Data
@EntityListeners(AuditEntityListener.class)
@Audited(entity = "USER")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="first_name", nullable = false)
    private String firstName;

    @Column(name="last_name", nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Transient
    private String password;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "invite_email_id")
    private Email inviteEmail;

    @Column(name="is_active")
    private Boolean isActive;
}