package com.verlake.dam.entity.jira.dto;

import lombok.Data;

/**
 * Request DTO for revoking access when Jira issue expires or is rejected
 */
@Data
public class JiraRevokeRequest {
    private String issueKey;
    private String reason; // "expired", "rejected", "manual"
}
