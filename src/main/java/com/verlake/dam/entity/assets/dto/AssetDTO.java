package com.verlake.dam.entity.assets.dto;

import com.verlake.dam.entity.assets.Asset;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class AssetDTO extends BaseAssetDTO {
    private AccessRequestSummaryDTO accessRequest;
    
    /**
     * Convert Asset entity to DTO
     */
    public static AssetDTO fromEntity(Asset asset) {
        if (asset == null) {
            return null;
        }
        
        AssetDTO dto = AssetDTO.builder().build();
        dto.populateBaseFields(asset);
        
        return dto;
    }
}