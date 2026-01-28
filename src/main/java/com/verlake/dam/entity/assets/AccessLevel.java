package com.verlake.dam.entity.assets;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.verlake.dam.enums.AssetType;
import com.verlake.dam.enums.DatabaseType;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "access_levels")
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AccessLevel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    private AssetType assetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "database_type", nullable = false)
    private DatabaseType databaseType;

    @Column(name = "object", nullable = false)
    private String object;

    @Column(name = "templates", columnDefinition = "text")
    /**
     * Sample values:
     * - "SELECT"
     * - "INSERT"
     * - "UPDATE"
     * - "DELETE"
     * - "FULL ACCESS"
     * - "READ ACCESS"
     */
    private String templates;

    @Column(name = "access_template", columnDefinition = "text")
    /**
     * Sample values:
     * MySQL: "GRANT SELECT ON $DB.$TABLE TO '$USER'@'%';"
     * PostgreSQL: "GRANT ALL PRIVILEGES ON DATABASE $DB TO $USER;"
     * MSSQL: "ALTER ROLE [db_owner] ADD MEMBER [$USER];"
     * 
     * Placeholders:
     * - $DB: Database name
     * - $TABLE: Table name
     * - $USER: Username
     * - $SCHEMA: Schema name (PostgreSQL)
     * - $PROCEDURE: Procedure name
     */
    private String accessTemplate;
} 