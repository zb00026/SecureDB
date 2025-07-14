package com.verlake.dam.entity.assets;

import com.verlake.dam.annotation.Audited;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.listener.AuditEntityListener;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.verlake.dam.enums.Roles;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "asset_credentials")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditEntityListener.class)
@Audited(entity = "ASSET_CREDENTIAL")
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class AssetCredential {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    private String username;

    private String password;

    @Column(name = "user_access_type")
    private String userAccessType;

    @Builder.Default
    @Column(name = "is_deleted", columnDefinition = "tinyint(1)")
    private Boolean isDeleted = false;

    @Builder.Default
    @Column(name = "is_temporary_password", columnDefinition = "tinyint(1)")
    private Boolean isTemporaryPassword = false;
} 