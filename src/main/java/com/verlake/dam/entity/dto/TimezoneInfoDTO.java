package com.verlake.dam.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimezoneInfoDTO {
    private String id;
    private String displayName;
    private String utcOffset;
}

