package com.verlake.dam.entity.assets;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "access_levels")
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AccessLevel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_type", nullable = false)
    private String assetType;

    @Column(name = "database_type", nullable = false)
    private String databaseType;

    @Column(name = "object", nullable = false)
    private String object;

    @Column(name = "templates", columnDefinition = "text")
    private String templates;

    @Column(name = "access_template", columnDefinition = "text")
    private String accessTemplate;
} 