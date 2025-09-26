package com.verlake.dam.entity.unix;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an ACL permission entry
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AclPermission {
    private String type; // group, user, etc.
    private String name;
    private String permissions; // rwx, rw, etc.
}
