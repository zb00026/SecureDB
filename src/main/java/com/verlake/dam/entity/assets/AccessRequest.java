package com.verlake.dam.entity.assets;

import com.verlake.dam.annotation.Audited;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.listener.AuditEntityListener;
import jakarta.persistence.*;
import lombok.Data;
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


    @Column(name = "access_sql", columnDefinition = "text")
    private String accessSql;

    @ManyToOne
    @JoinColumn(name = "requestor_id")
    private User requestor;

    @Column(name = "request_time")
    private LocalDateTime requestTime;

    @Column(name = "request_reason")
    private String requestReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "developer_approver_status")
    private ApprovalStatus developerApproverStatus = ApprovalStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_approver_status")
    private ApprovalStatus assetApproverStatus = ApprovalStatus.PENDING;

    // This is used to check if the password is a temporary password for developer request when approver approves the access request
    @Column(name = "is_temp_password")
    private Boolean isTempPassword;

    @OneToOne
    @JoinColumn(name = "asset_credential_id")
    private AssetCredential assetCredential;

    @Column(name = "expiry_hours")
    private Integer expiryHours = 2160; // Default 3 months in hours (3 * 30 * 24)

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

} 