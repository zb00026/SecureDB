package com.verlake.dam.entity.assets.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseSchemaDTO {
    private String databaseName;
    private List<TableSchemaDTO> tables;
    private int totalTables;
    private int totalColumns;
}
