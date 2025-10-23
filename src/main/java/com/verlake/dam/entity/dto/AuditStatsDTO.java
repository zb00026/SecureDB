package com.verlake.dam.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for audit trail statistics and chart data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditStatsDTO {
    
    private List<TimeSeriesData> timeSeriesData;
    private List<AssetActivityData> topAssets;
    private List<UserActivityData> topUsers;
    private List<IpActivityData> topIpAddresses;
    private long totalEvents;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSeriesData {
        private LocalDateTime timestamp;
        private long eventCount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetActivityData {
        private Long assetId;
        private String assetName;
        private long activityCount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserActivityData {
        private String userEmail;
        private long activityCount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IpActivityData {
        private String ipAddress;
        private long activityCount;
    }
}
