package com.verlake.dam.entity.assets.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnSchemaDTO {
    private String columnName;
    private String dataType;
    private String columnType;
    private boolean isNullable;
    private String columnDefault;
    private String columnComment;
    private String columnKey;
    private String extra;
    private int ordinalPosition;
}
