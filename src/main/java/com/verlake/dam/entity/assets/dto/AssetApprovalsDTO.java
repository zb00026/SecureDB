package com.verlake.dam.entity.assets.dto;

import com.verlake.dam.entity.assets.Asset;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class AssetApprovalsDTO extends BaseAssetDTO {
    
    /**
     * Convert Asset entity to DTO
     */
    public static AssetApprovalsDTO fromEntity(Asset asset) {
        if (asset == null) {
            return null;
        }
        
        AssetApprovalsDTO dto = AssetApprovalsDTO.builder().build();
        dto.populateBaseFields(asset);
        return dto;
    }
}