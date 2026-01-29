package com.verlake.dam.service.unix;

import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.dto.AccessRequestDTO;
import com.verlake.dam.entity.unix.UnixGroup;
import com.verlake.dam.entity.unix.UnixGroupMembership;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.enums.AssetType;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.repository.assets.AccessRequestRepository;
import com.verlake.dam.repository.assets.AssetCredentialsRepository;
import com.verlake.dam.repository.unix.UnixGroupRepository;
import com.verlake.dam.service.assets.AssetService;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing Unix access requests using the unified AccessRequest entity
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnixAccessRequestService {
    
    private final AccessRequestRepository accessRequestRepository;
    private final UnixGroupRepository unixGroupRepository;
    private final AssetService assetService;
    private final UserService userService;
    private final KeycloakService keycloakService;
    private final SSHKeyPairService sshKeyPairService;
    private final AssetCredentialsRepository assetCredentialsRepository;
    
    /**
     * Create a new Unix access request from a accessor
     * Generates SSH key pair if this is the first request for this user-asset combination
     */
    @Transactional
    public AccessRequest createAccessRequest(AccessRequestDTO createDTO) {
        log.info("Creating Unix access request for asset: {}", createDTO.getAssetId());
        
        // Validate asset
        Asset asset = assetService.findById(createDTO.getAssetId());
        if (asset.getType() != AssetType.UNIX_SERVER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset must be of type UNIX_SERVER");
        }
        
        // Get current user
        User requestor = userService.getCurrentUser();
        
        // Check if user already has an active request for this asset
        List<ApprovalStatus> activeStatuses = List.of(ApprovalStatus.REQUESTED, ApprovalStatus.APPROVAL_IN_PROGRESS, ApprovalStatus.APPROVED);
        accessRequestRepository.findActiveUnixRequestByAssetAndRequestor(asset, requestor, activeStatuses)
                .ifPresent(existingRequest -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, 
                            "You already have an active access request for this asset");
                });
        
        // Validate requested groups
        List<UnixGroup> requestedGroups = validateAndGetGroups(createDTO.getRequestedGroupIds(), asset);
        
        // Check if user needs new SSH key pair
        boolean needsNewKeyPair = !hasExistingSSHCredential(requestor, asset);
        String publicKey = null;
        String encryptedPrivateKey = null;
        
        if (needsNewKeyPair) {
            log.info("Generating new SSH key pair for user: {} and asset: {}", 
                    requestor.getEmail(), asset.getName());
            
            try {
                // Get user's Keycloak encryption key
                String userKey = keycloakService.getUserKey();
                if (userKey == null || userKey.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                            "User encryption key not available. Please re-login.");
                }
                
                // Generate SSH key pair
                SSHKeyPairService.SSHKeyPairResult keyPair = sshKeyPairService.generateKeyPair();
                
                // Store public key (unencrypted)
                publicKey = keyPair.getPublicKey();
                
                // Encrypt and store private key
                encryptedPrivateKey = sshKeyPairService.encryptPrivateKey(keyPair.getPrivateKey(), userKey);
                
                log.info("SSH key pair generated successfully. Fingerprint: {}", keyPair.getFingerprint());
                
            } catch (Exception e) {
                log.error("Failed to generate SSH key pair: {}", e.getMessage(), e);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                        "Failed to generate SSH key pair: " + e.getMessage());
            }
        } else {
            log.info("User already has SSH credentials for this asset, reusing existing keys");
        }
        
        // Create access request using the unified AccessRequest entity
        AccessRequest accessRequest = new AccessRequest();
        accessRequest.setAsset(asset);
        accessRequest.setRequestor(requestor);
        accessRequest.setRequestedUsername(createDTO.getRequestedUsername());
        accessRequest.setRequestTime(LocalDateTime.now());
        accessRequest.setRequestReason(createDTO.getRequestReason());
        accessRequest.setAccessorApproverStatus(ApprovalStatus.APPROVED); // Auto-approve for accessor
        accessRequest.setAssetApproverStatus(ApprovalStatus.REQUESTED); // Needs asset owner approval
        accessRequest.setPublicKey(publicKey);
        accessRequest.setEncryptedPrivateKey(encryptedPrivateKey);
        accessRequest.setExpiryHours(createDTO.getExpirationHours() != null && createDTO.getExpirationHours() > 0 
                ? createDTO.getExpirationHours() 
                : Constants.getTechnicalPropertyAsInt("unix.access.request.default.expiry.hours"));
        
        // Calculate expiry date
        accessRequest.setExpiryDate(accessRequest.getRequestTime().plusHours(accessRequest.getExpiryHours()));
        
        // Initialize group memberships list
        accessRequest.setGroupMemberships(new ArrayList<>());
        accessRequest.setIsTempPassword(true);
        
        // Save request first to get ID
        AccessRequest savedRequest = accessRequestRepository.save(accessRequest);
        
        // Add group memberships
        for (UnixGroup group : requestedGroups) {
            UnixGroupMembership membership = UnixGroupMembership.builder()
                    .accessRequest(savedRequest)
                    .unixGroup(group)
                    .status(ApprovalStatus.REQUESTED)
                    .approved(false)
                    .build();
            savedRequest.getGroupMemberships().add(membership);
        }
        
        // Save again with memberships
        savedRequest = accessRequestRepository.save(savedRequest);
        
        log.info("Unix access request created successfully. Request ID: {}, Groups: {}", 
                savedRequest.getId(), 
                requestedGroups.stream().map(UnixGroup::getGroupName).collect(Collectors.joining(", ")));
        
        return savedRequest;
    }
    
    /**
     * Validate and retrieve groups by IDs
     */
    private List<UnixGroup> validateAndGetGroups(List<Long> groupIds, Asset asset) {
        if (groupIds == null || groupIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "At least one group must be specified");
        }
        
        List<UnixGroup> groups = unixGroupRepository.findAllById(groupIds);
        
        if (groups.size() != groupIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "One or more group IDs are invalid");
        }
        
        // Verify all groups belong to the same asset
        boolean allGroupsBelongToAsset = groups.stream()
                .allMatch(group -> group.getAsset().getId().equals(asset.getId()));
        
        if (!allGroupsBelongToAsset) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "All groups must belong to the requested asset");
        }
        
        return groups;
    }
    
    /**
     * Check if user already has SSH credential for this asset
     */
    private boolean hasExistingSSHCredential(User user, Asset asset) {
        return assetCredentialsRepository.findByAssetIdAndUserId(asset.getId(), user.getId())
                .stream()
                .anyMatch(cred -> cred.getSshKeyFile() != null && !cred.getSshKeyFile().isEmpty());
    }
    
    /**
     * Get all access requests for an asset (for asset owner)
     */
    public Page<AccessRequest> getAccessRequestsForAsset(Long assetId, Pageable pageable) {
        Asset asset = assetService.findById(assetId);
        return accessRequestRepository.findUnixRequestsByAssetOrderByRequestTimeDesc(asset, pageable);
    }
    
    /**
     * Get all access requests by current user (for accessor)
     */
    public List<AccessRequest> getMyAccessRequests() {
        User currentUser = userService.getCurrentUser();
        return accessRequestRepository.findUnixRequestsByRequestorOrderByRequestTimeDesc(currentUser);
    }
    
    /**
     * Get pending access requests for assets owned by current user
     */
    public List<AccessRequest> getPendingRequestsForMyAssets() {
        User currentUser = userService.getCurrentUser();
        
        // Get all assets owned by current user
        List<Long> assetIds = assetCredentialsRepository.findByUserId(currentUser.getId())
                .stream()
                .filter(cred -> Roles.ASSET_OWNER.getOriginalName().equals(cred.getUserAccessType()))
                .map(cred -> cred.getAsset())
                .filter(asset -> asset.getType() == AssetType.UNIX_SERVER)
                .map(Asset::getId)
                .distinct()
                .collect(Collectors.toList());
        
        if (assetIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        return accessRequestRepository.findPendingUnixRequestsForAssets(assetIds, ApprovalStatus.REQUESTED);
    }
    
    /**
     * Get access request by ID
     */
    public AccessRequest getAccessRequestById(Long requestId) {
        return accessRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "Access request not found"));
    }
}
