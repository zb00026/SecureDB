package com.verlake.dam.controller.accessor;

import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.assets.dto.AccessRequestDTO;
import com.verlake.dam.entity.dto.unix.UnixGroupDTO;
import com.verlake.dam.service.unix.UnixAccessRequestService;
import com.verlake.dam.service.unix.UnixGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for accessors to request Unix asset access
 */
@RestController
@RequestMapping("/api/accessor/unix-access")
@Slf4j
@RequiredArgsConstructor
@PreAuthorize("hasRole('ACCESSOR')")
public class UnixAccessRequestController {
    
    private final UnixAccessRequestService unixAccessRequestService;
    private final UnixGroupService unixGroupService;
    
    /**
     * Get available Unix groups for an asset
     */
    @GetMapping("/assets/{assetId}/groups")
    public ResponseEntity<List<UnixGroupDTO>> getAvailableGroups(@PathVariable Long assetId) {
        log.info("Getting available Unix groups for asset: {}", assetId);
        List<UnixGroupDTO> groups = unixGroupService.getGroupsByAsset(assetId);
        return ResponseEntity.ok(groups);
    }
    
    /**
     * Create a new Unix access request
     */
    @PostMapping("/requests")
    public ResponseEntity<AccessRequest> createAccessRequest(
            @RequestBody AccessRequestDTO createDTO) {
        log.info("Creating Unix access request for asset: {}", createDTO.getAssetId());
        AccessRequest request = unixAccessRequestService.createAccessRequest(createDTO);
        return ResponseEntity.ok(request);
    }
    
    /**
     * Get my Unix access requests
     */
    @GetMapping("/requests/my")
    public ResponseEntity<List<AccessRequest>> getMyAccessRequests() {
        log.info("Getting my Unix access requests");
        List<AccessRequest> requests = unixAccessRequestService.getMyAccessRequests();
        return ResponseEntity.ok(requests);
    }
    
    /**
     * Get a specific Unix access request by ID
     */
    @GetMapping("/requests/{requestId}")
    public ResponseEntity<AccessRequest> getAccessRequest(@PathVariable Long requestId) {
        log.info("Getting Unix access request: {}", requestId);
        AccessRequest request = unixAccessRequestService.getAccessRequestById(requestId);
        return ResponseEntity.ok(request);
    }
}

