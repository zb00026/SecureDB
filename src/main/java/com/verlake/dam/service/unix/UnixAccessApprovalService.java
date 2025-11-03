package com.verlake.dam.service.unix;

import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.entity.unix.UnixGroup;
import com.verlake.dam.entity.unix.UnixGroupMembership;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.repository.assets.AssetCredentialsRepository;
import com.verlake.dam.repository.AuditTrailRepository;
import com.verlake.dam.repository.assets.AccessRequestRepository;
import com.verlake.dam.service.terminal.SSHConnectionService;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.AuditDescriptionUtils;
import com.verlake.dam.utils.Constants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for approving Unix access requests and creating Unix users
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnixAccessApprovalService {
    
    private final AccessRequestRepository accessRequestRepository;
    private final AssetCredentialsRepository assetCredentialsRepository;
    private final AuditTrailRepository auditTrailRepository;
    private final UserService userService;
    private final SSHKeyPairService sshKeyPairService;
    private final SSHConnectionService sshConnectionService;
    
    /**
     * Approve a Unix access request and create the user on the Unix system
     */
    @Transactional
    public void approveAccessRequest(Long requestId, List<Long> approvedGroupIds) {
        log.info("Approving Unix access request: {}", requestId);
        
        // Get request
        AccessRequest request = accessRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "Access request not found"));
        
        // Validate status
        if (request.getAssetApproverStatus() != ApprovalStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Access request is not in pending status");
        }
        
        // Validate approver is asset owner
        User approver = userService.getCurrentUser();
        if (!isAssetOwner(approver, request.getAsset())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                    "You are not an asset owner for this asset");
        }
        
        // Update group membership approvals
        updateGroupMembershipApprovals(request, approvedGroupIds);
        
        try {
            // Create Unix user
            createUnixUser(request);
            
            // Add user to approved groups
            addUserToGroups(request, approvedGroupIds);
            
            // Add public key to user
            addPublicKeyToUser(request);
            
            // Create AssetCredential for developer
            createDeveloperCredential(request);
            
            // Update request status
            request.setAssetApproverStatus(ApprovalStatus.APPROVED);
            request.setApprovedTime(LocalDateTime.now());
            request.setApprovedBy(approver);
            accessRequestRepository.save(request);
            
            // Create audit trail
            createAuditTrail(request, approver, "APPROVED");
            
            log.info("Unix access request approved successfully. Request ID: {}, Username: {}", 
                    requestId, request.getRequestedUsername());
            
        } catch (Exception e) {
            log.error("Failed to approve Unix access request: {}", e.getMessage(), e);
            
            // Create audit trail for failure
            createAuditTrail(request, approver, "APPROVAL_FAILED: " + e.getMessage());
            
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to create Unix user: " + e.getMessage());
        }
    }
    
    /**
     * Reject a Unix access request
     */
    @Transactional
    public void rejectAccessRequest(Long requestId, String rejectReason) {
        log.info("Rejecting Unix access request: {}", requestId);
        
        // Get request
        AccessRequest request = accessRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "Access request not found"));
        
        // Validate status
        if (request.getAssetApproverStatus() != ApprovalStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Access request is not in pending status");
        }
        
        // Validate approver is asset owner
        User approver = userService.getCurrentUser();
        if (!isAssetOwner(approver, request.getAsset())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                    "You are not an asset owner for this asset");
        }
        
        // Update request
        request.setAssetApproverStatus(ApprovalStatus.REJECTED);
        request.setRejectReason(rejectReason);
        request.setApprovedTime(LocalDateTime.now());
        request.setApprovedBy(approver);
        accessRequestRepository.save(request);
        
        // Create audit trail
        createAuditTrail(request, approver, "REJECTED: " + rejectReason);
        
        log.info("Unix access request rejected. Request ID: {}", requestId);
    }
    
    /**
     * Update group membership approvals
     */
    private void updateGroupMembershipApprovals(AccessRequest request, List<Long> approvedGroupIds) {
        for (UnixGroupMembership membership : request.getGroupMemberships()) {
            if (approvedGroupIds.contains(membership.getUnixGroup().getId())) {
                membership.setApproved(true);
                membership.setStatus(ApprovalStatus.APPROVED);
            } else {
                membership.setApproved(false);
                membership.setStatus(ApprovalStatus.REJECTED);
            }
        }
    }
    
    /**
     * Check if user is asset owner for the given asset
     */
    private boolean isAssetOwner(User user, Asset asset) {
        return assetCredentialsRepository.findByAssetIdAndUserId(asset.getId(), user.getId())
                .stream()
                .anyMatch(cred -> Roles.ASSET_OWNER.getOriginalName().equals(cred.getUserAccessType()));
    }
        
    /**
     * Create Unix user on the remote system
     */
    private void createUnixUser(AccessRequest request) throws Exception {
        String username = request.getRequestedUsername();
        Asset asset = request.getAsset();
        
        log.info("Creating Unix user: {} on asset: {}", username, asset.getName());
        
        // Check if user already exists
        String checkUserCommand = String.format("id %s 2>/dev/null || echo 'USER_NOT_EXISTS'", username);
        String checkResult = executeSSHCommand(asset, checkUserCommand);
        
        if (!checkResult.contains("USER_NOT_EXISTS")) {
            log.warn("User {} already exists on asset {}", username, asset.getName());
            return; // User already exists, skip creation
        }
        
        // Create user with home directory
        String createUserCommand = String.format("sudo useradd -m -s /bin/bash %s", username);
        String result = executeSSHCommand(asset, createUserCommand);
        
        log.info("Unix user created: {}. Result: {}", username, result);
    }
    
    /**
     * Add user to approved groups
     */
    private void addUserToGroups(AccessRequest request, 
                                  List<Long> approvedGroupIds) throws Exception {
        String username = request.getRequestedUsername();
        Asset asset = request.getAsset();
        
        // Get approved groups from membership list
        if (request.getGroupMemberships() == null || request.getGroupMemberships().isEmpty()) {
            log.warn("No group memberships found for request: {}", request.getId());
            return;
        }
        
        List<UnixGroup> approvedGroups = request.getGroupMemberships().stream()
                .filter(m -> approvedGroupIds.contains(m.getUnixGroup().getId()))
                .map(UnixGroupMembership::getUnixGroup)
                .toList();
        
        if (approvedGroups.isEmpty()) {
            log.warn("No groups approved for user: {}", username);
            return;
        }
        
        log.info("Adding user {} to groups: {}", username, 
                approvedGroups.stream().map(UnixGroup::getGroupName).collect(Collectors.joining(", ")));
        
        // Add user to each approved group
        for (UnixGroup group : approvedGroups) {
            String addToGroupCommand = String.format("sudo usermod -a -G %s %s", 
                    group.getGroupName(), username);
            String result = executeSSHCommand(asset, addToGroupCommand);
            
            log.info("Added user {} to group {}. Result: {}", username, group.getGroupName(), result);
        }
    }
    
    /**
     * Add public key to user's authorized_keys
     */
    private void addPublicKeyToUser(AccessRequest request) throws Exception {
        String username = request.getRequestedUsername();
        String publicKey = request.getPublicKey();
        Asset asset = request.getAsset();
        
        if (publicKey == null || publicKey.isEmpty()) {
            log.info("No new public key to add for user: {}", username);
            return; // User already has SSH credential, no new key to add
        }
        
        log.info("Adding public key to user: {}", username);
        
        // Format public key with comment
        String formattedKey = sshKeyPairService.formatPublicKeyForAuthorizedKeys(publicKey, 
                request.getRequestor().getEmail());
        
        // Create .ssh directory if it doesn't exist
        String createSshDirCommand = String.format(
                "sudo mkdir -p /home/%s/.ssh && sudo chmod 700 /home/%s/.ssh", 
                username, username);
        executeSSHCommand(asset, createSshDirCommand);
        
        // Add public key to authorized_keys
        String escapedKey = formattedKey.replace("'", "'\\''");
        String addKeyCommand = String.format(
                "echo '%s' | sudo tee -a /home/%s/.ssh/authorized_keys > /dev/null && " +
                "sudo chmod 600 /home/%s/.ssh/authorized_keys && " +
                "sudo chown -R %s:%s /home/%s/.ssh",
                escapedKey, username, username, username, username, username);
        String result = executeSSHCommand(asset, addKeyCommand);
        
        log.info("Public key added to user {}. Result: {}", username, result);
    }
    
    /**
     * Create AssetCredential for developer
     */
    private void createDeveloperCredential(AccessRequest request) {
        // Check if credential already exists
        boolean credentialExists = assetCredentialsRepository
                .findByAssetIdAndUserId(request.getAsset().getId(), request.getRequestor().getId())
                .stream()
                .anyMatch(cred -> Roles.DEVELOPER.getOriginalName().equals(cred.getUserAccessType()));
        
        if (credentialExists) {
            log.info("Developer credential already exists for user: {}", request.getRequestor().getEmail());
            return;
        }
        
        AssetCredential developerCredential = AssetCredential.builder()
                .asset(request.getAsset())
                .user(request.getRequestor())
                .username(request.getRequestedUsername())
                .sshKeyFile(request.getEncryptedPrivateKey()) // Store encrypted private key
                .userAccessType(Roles.DEVELOPER.getOriginalName())
                .isDeleted(false)
                .build();
        
        assetCredentialsRepository.save(developerCredential);
        
        log.info("Developer credential created for user: {}", request.getRequestor().getEmail());
    }
    
    /**
     * Execute SSH command on remote system using cached sessions
     */
    private String executeSSHCommand(Asset asset, String command) throws IOException {
        // Use SSHConnectionService for session caching and command execution
        return sshConnectionService.executeCommand(asset, command);
    }
    
    /**
     * Create audit trail for access request action
     */
    private void createAuditTrail(AccessRequest request, User approver, String action) {
        try {
            String instanceId = String.format("UNIX_ACCESS_REQUEST(%d)", request.getId());
            String metadata = String.format("Asset: %s, Requestor: %s, Username: %s, Groups: %s",
                    request.getAsset().getName(),
                    request.getRequestor().getEmail(),
                    request.getRequestedUsername(),
                    request.getGroupMemberships().stream()
                            .filter(UnixGroupMembership::getApproved)
                            .map(m -> m.getUnixGroup().getGroupName())
                            .collect(Collectors.joining(", ")));
            
        AuditTrail audit = AuditTrail.builder()
                    .timestamp(LocalDateTime.now())
                    .instanceId(instanceId)
                    .user(approver.getEmail())
                    .action(action)
                    .actionMetadata(metadata)
                    .asset(request.getAsset())
                    .synced(false)
                    .description(AuditDescriptionUtils.generateDescription(action, Constants.ENTITY_TYPE_ACCESS_REQUEST, null))
                    // readableDescription will be computed at read-time (DTO)
                    .build();
            
            auditTrailRepository.save(audit);
            
        } catch (Exception e) {
            log.error("Failed to create audit trail: {}", e.getMessage(), e);
        }
    }
}

