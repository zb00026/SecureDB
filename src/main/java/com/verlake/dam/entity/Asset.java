package com.verlake.dam.entity;

import com.verlake.dam.annotation.Audited;
import com.verlake.dam.enums.AssetType;
import com.verlake.dam.enums.DatabaseType;
import com.verlake.dam.listener.AuditEntityListener;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;

@Entity
@Table(name = "assets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditEntityListener.class)
@Audited(entity = "ASSET")

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

    @Column(name = "is_deleted")
    private boolean deleted;

    @OneToMany(mappedBy = "asset", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<AssetCredential> assetCredentials = new ArrayList<>();

    @Transient
    public List<User> getOwners() {
        if(assetCredentials != null) {
            return assetCredentials.stream()
                    .map(AssetCredential::getUser)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
} 