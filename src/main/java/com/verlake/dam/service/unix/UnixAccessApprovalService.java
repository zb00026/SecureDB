package com.verlake.dam.service.unix;

import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.unix.UnixGroup;
import com.verlake.dam.entity.unix.UnixGroupMembership;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.repository.AuditTrailRepository;
import com.verlake.dam.repository.assets.AccessRequestRepository;
import com.verlake.dam.repository.assets.AssetCredentialsRepository;
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
    private final SSHConnectionService sshConnectionService;
    private final SSHKeyPairService sshKeyPairService;
    
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
        
        // Validate status - can approve if REQUESTED or APPROVAL_IN_PROGRESS
        if (request.getAssetApproverStatus() != ApprovalStatus.REQUESTED 
                && request.getAssetApproverStatus() != ApprovalStatus.APPROVAL_IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Access request is not in a pending status for approval");
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
            
            // Generate temporary SSH key pair and add public key to authorized_keys
            String privateKey = generateAndAddSSHKeyPair(request);
            
            // Create AssetCredential for accessor with unencrypted private key
            // The private key will be encrypted later with accessor's key when they access it
            createAccessorCredential(request, privateKey);

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
        
        // Validate status - can approve if REQUESTED or APPROVAL_IN_PROGRESS
        if (request.getAssetApproverStatus() != ApprovalStatus.REQUESTED 
                && request.getAssetApproverStatus() != ApprovalStatus.APPROVAL_IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Access request is not in a pending status for approval");
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
        
        // Find bash executable path (common locations: /bin/bash, /usr/bin/bash)
        String bashPath = findBashPath(asset);
        log.debug("Using bash path: {} for user creation", bashPath);
        
        // Check if group with same name exists
        String checkGroupCommand = String.format("getent group %s >/dev/null 2>&1 && echo 'GROUP_EXISTS' || echo 'GROUP_NOT_EXISTS'", username);
        String groupCheckResult = executeSSHCommand(asset, checkGroupCommand);
        boolean groupExists = groupCheckResult.contains("GROUP_EXISTS");
        
        // Create user with home directory
        // If group exists, use -g to add user to that group, otherwise let useradd create the group
        String createUserCommand;
        if (groupExists) {
            createUserCommand = String.format("sudo useradd -m -s %s -g %s %s", bashPath, username, username);
        } else {
            createUserCommand = String.format("sudo useradd -m -s %s %s", bashPath, username);
        }
        String result = executeSSHCommand(asset, createUserCommand);
        
        log.info("Unix user created: {}. Result: {}", username, result);
    }
    
    /**
     * Find the bash executable path on the remote system
     * Tries common locations: /bin/bash, /usr/bin/bash
     * Falls back to system default shell if bash is not found
     */
    private String findBashPath(Asset asset) throws Exception {
        // Try common bash locations
        String[] bashPaths = {"/bin/bash", "/usr/bin/bash"};
        
        for (String bashPath : bashPaths) {
            String checkCommand = String.format("test -x %s && echo 'EXISTS' || echo 'NOT_EXISTS'", bashPath);
            String result = executeSSHCommand(asset, checkCommand);
            
            if (result.contains("EXISTS")) {
                log.debug("Found bash at: {}", bashPath);
                return bashPath;
            }
        }
        
        // If bash not found, try to find it using which/whereis
        String findBashCommand = "which bash 2>/dev/null || whereis -b bash 2>/dev/null | awk '{print $2}' | head -1 || echo ''";
        String bashLocation = executeSSHCommand(asset, findBashCommand).trim();
        
        if (!bashLocation.isEmpty() && !bashLocation.contains("not found") && !bashLocation.contains("no bash")) {
            log.debug("Found bash using which/whereis at: {}", bashLocation);
            return bashLocation.trim();
        }
        
        // Last resort: use system default shell (usually /bin/sh)
        log.warn("Bash not found on system, using default shell /bin/sh");
        return "/bin/sh";
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
     * Generate temporary SSH key pair and add public key to user's authorized_keys
     */
    private String generateAndAddSSHKeyPair(AccessRequest request) throws Exception {
        String username = request.getRequestedUsername();
        Asset asset = request.getAsset();
        
        log.info("Generating temporary SSH key pair for Unix user: {}", username);
        
        // Generate SSH key pair
        SSHKeyPairService.SSHKeyPairResult keyPair = sshKeyPairService.generateKeyPair();
        String publicKey = keyPair.getPublicKey();
        String privateKey = keyPair.getPrivateKey();
        
        log.info("SSH key pair generated successfully. Fingerprint: {}", keyPair.getFingerprint());
        
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
        
        // Return unencrypted private key - will be encrypted later with accessor's key
        return privateKey;
    }
    
    /**
     * Create AssetCredential for accessor with unencrypted private key
     * The private key will be encrypted later with accessor's key when they access it
     */
    private void createAccessorCredential(AccessRequest request, String privateKey) {
        // Check if credential already exists
        boolean credentialExists = assetCredentialsRepository
                .findByAssetIdAndUserId(request.getAsset().getId(), request.getRequestor().getId())
                .stream()
                .anyMatch(cred -> Roles.ACCESSOR.getOriginalName().equals(cred.getUserAccessType()));
        
        if (credentialExists) {
            AssetCredential devCredential = assetCredentialsRepository
                    .findByUserIdAndAssetIdAndUserAccessType(request.getRequestor().getId(), request.getAsset().getId(), Roles.ACCESSOR.getOriginalName())
                    .orElse(null);
            if (devCredential != null) {
                devCredential.setUsername(request.getRequestedUsername());
                devCredential.setIsTemporaryPassword(true);
                devCredential.setSshKeyFile(privateKey);
                assetCredentialsRepository.saveAndFlush(devCredential);
            }
            log.info("Accessor credential already exists for user: {}", request.getRequestor().getEmail());
            return;
        }
        
        AssetCredential accessorCredential = AssetCredential.builder()
                .asset(request.getAsset())
                .user(request.getRequestor())
                .username(request.getRequestedUsername())
                .sshKeyFile(privateKey) // Store unencrypted private key - will be encrypted with accessor's key later
                .userAccessType(Roles.ACCESSOR.getOriginalName())
                .isDeleted(false)
                .build();
        
        assetCredentialsRepository.save(accessorCredential);
        
        log.info("Accessor credential created with SSH key pair for user: {}", 
                request.getRequestor().getEmail());
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

