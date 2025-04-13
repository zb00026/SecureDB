package com.verlake.dam.entity.assets;

import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.ApprovalStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "access_requests")
@Data
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

} 