package com.verlake.dam.entity.user;


import com.verlake.dam.entity.user.dto.UserApproverDTO;
import jakarta.persistence.*;
import lombok.Data;

import com.verlake.dam.listener.AuditEntityListener;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.annotation.Audited;
import com.verlake.dam.entity.Email;
import com.verlake.dam.entity.Role;
import com.verlake.dam.utils.SpringContext;

import java.util.*;

@Entity
@Table(name = "users")
@Data
@EntityListeners(AuditEntityListener.class)
@Audited(entity = "USER")

public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="first_name", nullable = false)
    private String firstName;

    @Column(name="last_name", nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Transient
    private String password;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "invite_email_id")
    private Email inviteEmail;

    @Column(name="invite_code")
    private String inviteCode;

    @Column(name="is_active", columnDefinition = "tinyint(1)")
    private Boolean isActive;

    @Column(name = "is_initial_password", columnDefinition = "tinyint(1)")
    private Boolean isInitialPassword;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "deleted", columnDefinition = "tinyint(1) default 0")
    private Boolean deleted = false;

    @Transient
    private UserApproverDTO approver;

    public UserApproverDTO getApprover() {
        if (approverId != null) {
            approver = SpringContext.getBean(UserService.class).findById(approverId).toApproverDTO();
        }
        return approver;
    }

    public void setApprover(UserApproverDTO approver) {
        this.approver = approver;
        this.approverId = approver != null ? approver.getId() : null;
    }

    public UserApproverDTO toApproverDTO() {
        UserApproverDTO userApproverDTO = new UserApproverDTO();
        userApproverDTO.setId(this.getId());
        userApproverDTO.setFirstName(this.getFirstName());
        userApproverDTO.setLastName(this.getLastName());
        userApproverDTO.setEmail(this.getEmail());
        return userApproverDTO;
    }

    /**
     * Get user status based on active state and invite code
     * @return "Active" if user is active, "Invite sent" if user has invite code but not active, "Inactive" otherwise
     */
    @Transient
    public String getStatus() {
        if (Boolean.TRUE.equals(this.deleted)) {
            return com.verlake.dam.utils.Constants.USER_STATUS_DELETED;
        }
        if (Boolean.TRUE.equals(this.isActive)) {
            return com.verlake.dam.utils.Constants.USER_STATUS_ACTIVE;
        }
        // If user is not active but has invite code, it means invite was sent
        if (this.inviteCode != null && !this.inviteCode.isEmpty()) {
            return com.verlake.dam.utils.Constants.USER_STATUS_INVITE_SENT;
        }
        // Otherwise, user is inactive (was activated but then deactivated)
        return com.verlake.dam.utils.Constants.USER_STATUS_INACTIVE;
    }
}