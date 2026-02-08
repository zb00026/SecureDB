package com.verlake.dam.entity.assets.dto;

import com.verlake.dam.entity.assets.AssetQueryChangeRequest;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.ApprovalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetQueryChangeRequestDTO {
    private Long id;
    private AssetDTO asset;
    private User requestor;
    private String ticketReference;
    private String changeDescription;
    private ApprovalStatus approvalStatus;
    private String rejectReason;
    private String query;

    /**
     * Convert AssetQueryChangeRequest entity to DTO
     */
    public static AssetQueryChangeRequestDTO fromEntity(AssetQueryChangeRequest entity) {
        if (entity == null) {
            return null;
        }

        return AssetQueryChangeRequestDTO.builder()
                .id(entity.getId())
                .asset(entity.getAsset() != null ? AssetDTO.fromEntity(entity.getAsset()) : null)
                .requestor(entity.getRequestor())
                .ticketReference(entity.getTicketReference())
                .changeDescription(entity.getChangeDescription())
                .approvalStatus(entity.getApprovalStatus())
                .rejectReason(entity.getRejectReason())
                .query(entity.getQuery())
                .build();
    }
}
