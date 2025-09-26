package com.verlake.dam.entity.unix;

import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.user.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a Unix group on a specific asset
 */
@Entity
@Table(name = "unix_groups", 
       uniqueConstraints = @UniqueConstraint(name = "uk_unix_groups_name_asset", 
                                           columnNames = {"group_name", "asset_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnixGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false, foreignKey = @ForeignKey(name = "fk_unix_groups_asset"))
    private Asset asset;

    @Column(name = "group_name", nullable = false, length = 255)
    private String groupName;

    @Column(name = "group_id")
    private Integer groupId;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_system_group", nullable = false, columnDefinition = "TINYINT(1)")
    @Builder.Default
    private Boolean isSystemGroup = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false, foreignKey = @ForeignKey(name = "fk_unix_groups_created_by"))
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @OneToMany(mappedBy = "unixGroup", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<UnixGroupFolderAccess> folderAccesses = new ArrayList<>();

    /**
     * Add folder access to this group
     */
    public void addFolderAccess(UnixGroupFolderAccess folderAccess) {
        folderAccesses.add(folderAccess);
        folderAccess.setUnixGroup(this);
    }

    /**
     * Remove folder access from this group
     */
    public void removeFolderAccess(UnixGroupFolderAccess folderAccess) {
        folderAccesses.remove(folderAccess);
        folderAccess.setUnixGroup(null);
    }

    /**
     * Check if this group has access to a specific folder
     */
    public boolean hasAccessToFolder(String folderPath) {
        return folderAccesses.stream()
                .anyMatch(access -> access.getFolderPath().equals(folderPath));
    }

    /**
     * Get access type for a specific folder
     */
    public UnixGroupFolderAccess.AccessType getAccessTypeForFolder(String folderPath) {
        return folderAccesses.stream()
                .filter(access -> access.getFolderPath().equals(folderPath))
                .map(UnixGroupFolderAccess::getAccessType)
                .findFirst()
                .orElse(null);
    }

    /**
     * Check if this is a custom group (not system group)
     */
    public boolean isCustomGroup() {
        return !isSystemGroup;
    }

    /**
     * Update sync timestamp
     */
    public void markAsSynced() {
        this.lastSyncedAt = LocalDateTime.now();
    }
}
