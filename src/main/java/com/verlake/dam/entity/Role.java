package com.verlake.dam.entity;

import com.verlake.dam.annotation.Audited;
import com.verlake.dam.listener.AuditEntityListener;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "roles")
@EntityListeners(AuditEntityListener.class)
@Audited(entity = "ROLE")
@Data
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

}
