package com.verlake.dam.entity.jira.dto;

import lombok.Data;

/**
 * Request DTO for provisioning access when Jira issue is approved
 */
@Data
public class JiraProvisionRequest {
    private String issueKey;
    private String issueId;
    private String approverAccountId; // Jira User Account ID (secure identifier from webhook)
    private String approverEmail; // Approver email from webhook (verified via HMAC signature)
    private Long timestamp;
}
