package com.verlake.dam.entity.unix;

import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.enums.ApprovalStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a requested group membership for Unix access
 */
@Entity
@Table(name = "unix_group_memberships")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnixGroupMembership {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "access_request_id", nullable = false)
    @JsonIgnore
    private AccessRequest accessRequest;
    
    @ManyToOne
    @JoinColumn(name = "unix_group_id", nullable = false)
    private UnixGroup unixGroup;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ApprovalStatus status = ApprovalStatus.REQUESTED;
    
    @Column(name = "approved")
    @Builder.Default
    private Boolean approved = false;
}

