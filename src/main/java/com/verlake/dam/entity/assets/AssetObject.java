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

    @Column(name = "objects_json", columnDefinition = "LONGTEXT")
    @Lob
    /**
     * JSON structure containing database objects and their access grants.
     * Format:
     * {
     *   "VIEW": {
     *     "data": [], // Array of view names
     *     "grants": [
     *       {
     *         "access_template": "VIEW privileges from global grant"
     *       }
     *     ]
     *   },
     *   "TABLE": {
     *     "data": [], // Array of table names
     *     "grants": [
     *       {
     *         "access_template": "TABLE privileges from global grant"
     *       }
     *     ]
     *   },
     *   "DATABASE": {
     *     "data": [], // Array of database names
     *     "grants": [
     *       {
     *         "access_template": "Database privileges grant statement"
     *       }
     *     ]
     *   },
     *   "PROCEDURE": {
     *     "data": [], // Array of procedure names
     *     "grants": [
     *       {
     *         "access_template": "PROCEDURE privileges from global grant"
     *       }
     *     ]
     *   }
     * }
     * 
     * Each object type (VIEW, TABLE, DATABASE, PROCEDURE) contains:
     * - data: Array of object names
     * - grants: Array of grant statements with access_template
     */
    private String objectsJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;
} 