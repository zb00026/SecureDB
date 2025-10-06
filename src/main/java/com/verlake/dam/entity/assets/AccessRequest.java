package com.verlake.dam.entity.assets;

import com.verlake.dam.annotation.Audited;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.listener.AuditEntityListener;
import com.verlake.dam.entity.assets.dto.AssetDTO;
import com.verlake.dam.entity.unix.UnixGroupMembership;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "access_requests")
@Data
@EntityListeners(AuditEntityListener.class)
@Audited(entity = "ACCESS_REQUEST")
public class AccessRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "asset_id")
    private Asset asset;
    
    @Transient
    private AssetDTO assetDTO;

    @Column(name = "access_sql", columnDefinition = "text")
    private String accessSql;

    @ManyToOne
    @JoinColumn(name = "requestor_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User requestor;

    @Column(name = "request_time")
    private LocalDateTime requestTime;

    @Column(name = "request_reason")
    private String requestReason;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "developer_approver_status")
    private ApprovalStatus developerApproverStatus = ApprovalStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_approver_status")
    private ApprovalStatus assetApproverStatus = ApprovalStatus.PENDING;

    // This is used to check if the password is a temporary password for developer request when approver approves the access request
    @Column(name = "is_temp_password", columnDefinition = "tinyint(1)")
    private Boolean isTempPassword;

    @OneToOne
    @JoinColumn(name = "asset_credential_id")
    private AssetCredential assetCredential;

    @Column(name = "expiry_hours")
    private Integer expiryHours = 2160; // Default 3 months in hours (3 * 30 * 24)

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;
    
    // Unix access request fields
    @Column(name = "requested_username")
    private String requestedUsername;
    
    @Column(name = "public_key", columnDefinition = "text")
    private String publicKey;
    
    @Column(name = "encrypted_private_key", columnDefinition = "text")
    private String encryptedPrivateKey;
    
    @Column(name = "approved_time")
    private LocalDateTime approvedTime;
    
    @ManyToOne
    @JoinColumn(name = "approved_by_id")
    private User approvedBy;
    
    @OneToMany(mappedBy = "accessRequest", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<UnixGroupMembership> groupMemberships;

} 