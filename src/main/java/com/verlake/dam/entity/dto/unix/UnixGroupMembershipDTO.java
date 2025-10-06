package com.verlake.dam.entity.dto.unix;

import com.verlake.dam.enums.ApprovalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Unix group membership information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnixGroupMembershipDTO {
    
    private Long id;
    
    private Long unixGroupId;
    
    private String groupName;
    
    private String groupDescription;
    
    private ApprovalStatus status;
    
    private Boolean approved;
}
