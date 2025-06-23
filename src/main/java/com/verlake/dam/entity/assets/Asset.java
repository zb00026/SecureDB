package com.verlake.dam.entity.assets;

import com.verlake.dam.annotation.Audited;
import com.verlake.dam.enums.AssetType;
import com.verlake.dam.enums.DatabaseType;
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

    private String hostAddress;

    @Column(name = "port_number")
    private String portNumber;

    @Column(name = "database_name")
    private String databaseName;

    @Column(name = "is_deleted")
    private boolean deleted;

    @Column(name = "is_locked")
    @Builder.Default
    private boolean locked = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "lock_type")
    private LockType lockType;

    public String getHostUrl() {
        StringBuilder url = new StringBuilder();
        
        if (hostAddress != null && !hostAddress.isEmpty()) {
            url.append(hostAddress);
            
            if (portNumber != null && !portNumber.isEmpty()) {
                url.append(":").append(portNumber);
            }
            
            if (databaseName != null && !databaseName.isEmpty()) {
                url.append("/").append(databaseName);
            }
        }
        
        return url.toString();
    }
} 