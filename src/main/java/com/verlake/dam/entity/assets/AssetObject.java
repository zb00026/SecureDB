package com.verlake.dam.entity.assets;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "asset_objects")
@Data
public class AssetObject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credential_id", nullable = false)
    private AssetCredential assetCredential;

    @Column(name = "objects_json", columnDefinition = "json")
    private String objectsJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;
} 