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
public class TableSchemaDTO {
    private String tableName;
    private String tableType;
    private String tableComment;
    private List<ColumnSchemaDTO> columns;
    private int columnCount;
    private String schema;
}
