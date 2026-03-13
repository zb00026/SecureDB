package com.verlake.dam.entity.jira.dto;

import lombok.Data;

import java.util.List;

/**
 * Request DTO for resolving the asset owner from a list of Jira role member emails.
 */
@Data
public class JiraAssetOwnerRequest {
    private Long assetId;
    private List<String> emails;
    private List<String> accountIds;
}
