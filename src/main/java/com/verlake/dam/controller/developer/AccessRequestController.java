package com.verlake.dam.controller.developer;

import com.verlake.dam.entity.assets.AccessLevelObject;
import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.dto.AccessRequestDTO;
import com.verlake.dam.entity.assets.dto.AssetDTO;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.service.AccessLevelService;
import com.verlake.dam.service.UserService;
import com.verlake.dam.utils.CommonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.verlake.dam.service.AccessRequestService;
import com.verlake.dam.service.AssetService;

import java.util.List;

import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.extern.slf4j.Slf4j;
@RestController
@RequestMapping("/api/developer/assets")
@RequiredArgsConstructor
@Slf4j
public class AccessRequestController {
    @Autowired
    private final AccessRequestService accessRequestService;

    @Autowired
    private final AssetService assetService;

    @Autowired
    private final AccessLevelService accessLevelService;

    @Autowired
    private UserService userService;

    @GetMapping
    public ResponseEntity<List<AssetDTO>> getAllAssets() {
        return ResponseEntity.ok(assetService.getAllAssetsWithFetchAccessTemplate());
    }

    @GetMapping("/{assetId}")
    public ResponseEntity<AssetDTO> getAsset(@PathVariable long assetId) {
        return ResponseEntity.ok(assetService.findDTOById(assetId));
    }


    @GetMapping("/{assetId}/asset_objects")
    public ResponseEntity<ArrayNode> getAvailableAssetObjects(@PathVariable Long assetId) {
        Asset asset = assetService.findById(assetId);
        return ResponseEntity.ok(accessLevelService.getAssetObjectsWithData(asset));
    }

    @GetMapping("/{accessRequestId}/access_level_objects")
    public ResponseEntity<List<AccessLevelObject>> getAccessLevelObjects(@PathVariable Long accessRequestId) {
        AccessRequest request = accessRequestService.findById(accessRequestId);
        return ResponseEntity.ok(accessLevelService.getAccessLevelObjects(request));
    }

    @PostMapping("/request")
    public AccessRequest saveAccessRequest(@RequestBody AccessRequestDTO accessRequest) {
        User requestor = userService.findByEmail(CommonUtils.getEmailFromSession());
        return accessRequestService.saveAccessRequest(accessRequest, requestor);
    }
} 