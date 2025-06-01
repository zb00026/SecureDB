package com.verlake.dam.entity.assets.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteQueryAlertData {
    private String executorName;
    private String executionTime;
    private String tableName;
    private String affectedRows;
    private String query;
    private String databaseType;
    private String hostUrl;
} 