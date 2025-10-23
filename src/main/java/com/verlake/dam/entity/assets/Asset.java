package com.verlake.dam.entity.assets;

import com.verlake.dam.annotation.Audited;
import com.verlake.dam.enums.AssetType;
import com.verlake.dam.enums.DatabaseType;
import com.verlake.dam.enums.UnixServerType;
import com.verlake.dam.enums.LockType;
import com.verlake.dam.listener.AuditEntityListener;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "assets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditEntityListener.class)
@Audited(entity = "ASSET")
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")

public class Asset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetType type;

    @Enumerated(EnumType.STRING)
    private DatabaseType databaseType;

    @Enumerated(EnumType.STRING)
    @Column(name = "unix_server_type")
    private UnixServerType unixServerType;

    private String hostAddress;

    @Column(name = "port_number")
    private String portNumber;

    @Column(name = "database_name")
    private String databaseName;

    @Column(name = "is_deleted", columnDefinition = "tinyint(1)")
    private boolean deleted;

    @Column(name = "is_locked", columnDefinition = "tinyint(1)")
    @Builder.Default
    private boolean locked = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "lock_type")
    private LockType lockType;

    @Column(name = "record_count_limit")
    @Builder.Default
    private Integer recordCountLimit = 50;

    public String getHostUrl() {
        if (!isValidHostAddress()) {
            return "";
        }
        
        StringBuilder url = new StringBuilder(hostAddress);
        appendPortIfPresent(url);
        
        if (type == AssetType.DATABASE) {
            appendDatabaseIfPresent(url);
        }
        
        return url.toString();
    }
    
    private boolean isValidHostAddress() {
        return hostAddress != null && !hostAddress.isEmpty();
    }
    
    private void appendPortIfPresent(StringBuilder url) {
        if (portNumber != null && !portNumber.isEmpty()) {
            url.append(":").append(portNumber);
        }
    }
    
    private void appendDatabaseIfPresent(StringBuilder url) {
        if (databaseName != null && !databaseName.isEmpty()) {
            if (databaseType == DatabaseType.SQLSERVER) {
                url.append(";databaseName=").append(databaseName);
            } else {
                url.append("/").append(databaseName);
            }
        }
    }
} 