package com.verlake.dam.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.verlake.dam.entity.assets.Asset;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_trails")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AuditTrail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "instance_id")
    private String instanceId;

    @Column(name = "user_email", nullable = false)
    private String user;

    @Column(nullable = false)
    private String action;

    @Column(name = "previous_value", columnDefinition = "TEXT")
    private String previousValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "action_metadata", columnDefinition = "TEXT")
    private String actionMetadata;

    @Column(name = "synced", columnDefinition = "tinyint(1)")
    private boolean synced;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "readable_description", columnDefinition = "TEXT")
    private String readableDescription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    @JsonIgnore
    private Asset asset;

    /**
     * Get asset ID for JSON serialization without triggering lazy loading
     */
    @JsonProperty("assetId")
    public Long getAssetId() {
        return asset != null ? asset.getId() : null;
    }
} 