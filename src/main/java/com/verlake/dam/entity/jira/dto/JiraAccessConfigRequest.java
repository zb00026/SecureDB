package com.verlake.dam.entity.jira.dto;

import lombok.Data;

/**
 * Request DTO for saving access configuration from Jira
 */
@Data
public class JiraAccessConfigRequest {
    private String issueKey;
    private String issueId;
    private String jiraAccountId; // Jira User Account ID (secure identifier from webhook)
    private String userEmail; // User email from webhook (verified via HMAC signature)
    private Long assetId;
    private String tables; // JSON array or comma-separated
    private String accessLevel;
    private Integer durationHours;
    private String businessJustification;
}
