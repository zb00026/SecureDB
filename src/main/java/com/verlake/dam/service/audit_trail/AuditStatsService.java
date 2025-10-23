package com.verlake.dam.service.audit_trail;

import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.dto.AuditStatsDTO;
import com.verlake.dam.entity.dto.AuditTrailFilter;
import com.verlake.dam.entity.dto.RoleBasedAuditTrailFilter;
import com.verlake.dam.service.audit_trail.RoleBasedAuditTrailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for generating audit trail statistics and chart data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditStatsService {

    private final RoleBasedAuditTrailService roleBasedAuditTrailService;
    private final AuditTrailService auditTrailService;

    /**
     * Generate audit statistics with role-based filtering
     */
    public AuditStatsDTO generateAuditStats(RoleBasedAuditTrailFilter filter) {
        log.info("Generating audit stats with filter: {}", filter);
        
        // Validate access before processing
        roleBasedAuditTrailService.validateAuditAccess();
        
        // Get all audit trails (without pagination for stats)
        filter.setPage(0);
        filter.setPerPage(Integer.MAX_VALUE);
        Page<AuditTrail> auditTrails = roleBasedAuditTrailService.getAuditTrails(filter);
        
        return generateStatsFromAuditTrails(auditTrails.getContent());
    }

    /**
     * Generate audit statistics for admin users (full access)
     */
    public AuditStatsDTO generateAllAuditStats(AuditTrailFilter filter) {
        log.info("Generating all audit stats with filter: {}", filter);
        
        // Get all audit trails (without pagination for stats)
        filter.setPage(0);
        filter.setPerPage(Integer.MAX_VALUE);
        Page<AuditTrail> auditTrails = auditTrailService.findAll(filter);
        
        return generateStatsFromAuditTrails(auditTrails.getContent());
    }

    /**
     * Generate statistics from audit trail data
     */
    private AuditStatsDTO generateStatsFromAuditTrails(List<AuditTrail> auditTrails) {
        if (auditTrails.isEmpty()) {
            return AuditStatsDTO.builder()
                    .totalEvents(0)
                    .build();
        }

        // Generate time series data (group by hour)
        List<AuditStatsDTO.TimeSeriesData> timeSeriesData = generateTimeSeriesData(auditTrails);
        
        // Generate top assets data
        List<AuditStatsDTO.AssetActivityData> topAssets = generateTopAssetsData(auditTrails);
        
        // Generate top users data
        List<AuditStatsDTO.UserActivityData> topUsers = generateTopUsersData(auditTrails);
        
        // Generate top IP addresses data
        List<AuditStatsDTO.IpActivityData> topIpAddresses = generateTopIpAddressesData(auditTrails);
        
        // Get date range
        LocalDateTime startDate = auditTrails.stream()
                .map(AuditTrail::getTimestamp)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
                
        LocalDateTime endDate = auditTrails.stream()
                .map(AuditTrail::getTimestamp)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());

        return AuditStatsDTO.builder()
                .timeSeriesData(timeSeriesData)
                .topAssets(topAssets)
                .topUsers(topUsers)
                .topIpAddresses(topIpAddresses)
                .totalEvents(auditTrails.size())
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }

    /**
     * Generate time series data grouped by hour
     */
    private List<AuditStatsDTO.TimeSeriesData> generateTimeSeriesData(List<AuditTrail> auditTrails) {
        Map<String, Long> hourlyCounts = auditTrails.stream()
                .collect(Collectors.groupingBy(
                    audit -> audit.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00:00")),
                    Collectors.counting()
                ));

        return hourlyCounts.entrySet().stream()
                .map(entry -> AuditStatsDTO.TimeSeriesData.builder()
                        .timestamp(LocalDateTime.parse(entry.getKey(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                        .eventCount(entry.getValue())
                        .build())
                .sorted((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()))
                .collect(Collectors.toList());
    }

    /**
     * Generate top 10 assets by activity count
     */
    private List<AuditStatsDTO.AssetActivityData> generateTopAssetsData(List<AuditTrail> auditTrails) {
        Map<Asset, Long> assetCounts = auditTrails.stream()
                .filter(audit -> audit.getAsset() != null)
                .collect(Collectors.groupingBy(
                    AuditTrail::getAsset,
                    Collectors.counting()
                ));

        return assetCounts.entrySet().stream()
                .map(entry -> AuditStatsDTO.AssetActivityData.builder()
                        .assetId(entry.getKey().getId())
                        .assetName(entry.getKey().getName())
                        .activityCount(entry.getValue())
                        .build())
                .sorted((a, b) -> Long.compare(b.getActivityCount(), a.getActivityCount()))
                .limit(10)
                .collect(Collectors.toList());
    }

    /**
     * Generate top 10 users by activity count
     */
    private List<AuditStatsDTO.UserActivityData> generateTopUsersData(List<AuditTrail> auditTrails) {
        Map<String, Long> userCounts = auditTrails.stream()
                .collect(Collectors.groupingBy(
                    AuditTrail::getUser,
                    Collectors.counting()
                ));

        return userCounts.entrySet().stream()
                .map(entry -> AuditStatsDTO.UserActivityData.builder()
                        .userEmail(entry.getKey())
                        .activityCount(entry.getValue())
                        .build())
                .sorted((a, b) -> Long.compare(b.getActivityCount(), a.getActivityCount()))
                .limit(10)
                .collect(Collectors.toList());
    }

    /**
     * Generate top 10 IP addresses by activity count
     */
    private List<AuditStatsDTO.IpActivityData> generateTopIpAddressesData(List<AuditTrail> auditTrails) {
        Map<String, Long> ipCounts = auditTrails.stream()
                .filter(audit -> audit.getIpAddress() != null && !audit.getIpAddress().isEmpty())
                .collect(Collectors.groupingBy(
                    AuditTrail::getIpAddress,
                    Collectors.counting()
                ));

        return ipCounts.entrySet().stream()
                .map(entry -> AuditStatsDTO.IpActivityData.builder()
                        .ipAddress(entry.getKey())
                        .activityCount(entry.getValue())
                        .build())
                .sorted((a, b) -> Long.compare(b.getActivityCount(), a.getActivityCount()))
                .limit(10)
                .collect(Collectors.toList());
    }
}
