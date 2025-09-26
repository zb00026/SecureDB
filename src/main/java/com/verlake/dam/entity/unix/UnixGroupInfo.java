package com.verlake.dam.entity.unix;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Information about a Unix group retrieved from the server
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnixGroupInfo {
    private String groupName;
    private Integer groupId;
    private String description;
    private Boolean isSystemGroup;
    private List<String> members;
}
