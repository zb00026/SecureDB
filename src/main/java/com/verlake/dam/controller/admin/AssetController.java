package com.verlake.dam.controller.admin;

import com.verlake.dam.entity.Asset;
import com.verlake.dam.entity.dto.AssetDTO;
import com.verlake.dam.entity.dto.AssetOwnerUpdateDTO;
import com.verlake.dam.service.AssetService;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;


@RestController
@RequestMapping("/api/admin/assets")
public class AssetController {
    private final AssetService assetService;

    @Autowired
    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping
    public ResponseEntity<List<Asset>> getAllAssets() {
        return ResponseEntity.ok(assetService.getAllAssets());
    }
    
    @PostMapping
    public ResponseEntity<Asset> createAsset(@RequestBody AssetDTO assetDTO) {
        return ResponseEntity.ok(assetService.createAsset(assetDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateAsset(@PathVariable Long id, @RequestBody AssetDTO updateDTO) {
        assetService.updateAsset(id, updateDTO);
        return CommonUtils.getSuccessResponse();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteAsset(@PathVariable Long id) {
        assetService.deleteAsset(id);
        return CommonUtils.getSuccessResponse();
    }

    @PostMapping("/{id}/owners")
    public ResponseEntity<Map<String, Object>> updateAssetOwners(
            @PathVariable Long id,
            @RequestBody AssetOwnerUpdateDTO updateDTO) {
        updateDTO.setAssetId(id);
        assetService.updateAssetOwners(updateDTO);
        return CommonUtils.getSuccessResponse();
    }
} 