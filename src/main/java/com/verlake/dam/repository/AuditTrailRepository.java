package com.verlake.dam.repository;

import com.verlake.dam.entity.AuditTrail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Repository
public interface AuditTrailRepository extends JpaRepository<AuditTrail, Long>, JpaSpecificationExecutor<AuditTrail> {
    List<AuditTrail> findBySyncedFalse();
    
    @Query("SELECT a FROM AuditTrail a WHERE a.synced = true AND a.timestamp < ?1")
    List<AuditTrail> findSyncedRecordsOlderThan(LocalDateTime date);
    
    @Modifying
    @Query("DELETE FROM AuditTrail a WHERE a.synced = true AND a.timestamp < ?1")
    void deleteSyncedRecordsOlderThan(LocalDateTime date);

    @Query("select distinct a.action from AuditTrail a")
    List<String> findDistinctActions();

    @Query("select distinct a.action from AuditTrail a " +
           "where (:emails is null or a.user in :emails) " +
           "and (:assetIds is null or (a.asset.id is not null and a.asset.id in :assetIds))")
    List<String> findDistinctActionsFiltered(Set<String> emails, Set<Long> assetIds);

    List<AuditTrail> findBySyncedFalseAndTimestampBefore(LocalDateTime cutoff);
} 