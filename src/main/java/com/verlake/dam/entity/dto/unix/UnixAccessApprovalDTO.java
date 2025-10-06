package com.verlake.dam.entity.dto.unix;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for Unix access request approval/rejection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnixAccessApprovalDTO {
    
    /**
     * List of approved group IDs
     */
    private List<Long> approvedGroupIds;
    
    /**
     * Reason for rejection (optional)
     */
    private String rejectReason;
    
    /**
     * Additional comments from the asset owner
     */
    private String comments;
}
