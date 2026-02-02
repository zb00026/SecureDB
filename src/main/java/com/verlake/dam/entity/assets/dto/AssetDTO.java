package com.verlake.dam.entity.assets.dto;

import com.verlake.dam.entity.assets.Asset;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
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