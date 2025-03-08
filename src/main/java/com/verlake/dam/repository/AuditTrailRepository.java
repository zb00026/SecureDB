package com.verlake.dam.repository;

import com.verlake.dam.entity.AuditTrail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditTrailRepository extends JpaRepository<AuditTrail, Long> {
    List<AuditTrail> findBySyncedFalse();
    
    @Query("SELECT a FROM AuditTrail a WHERE a.synced = true AND a.timestamp < ?1")
    List<AuditTrail> findSyncedRecordsOlderThan(LocalDateTime date);
    
    @Modifying
    @Query("DELETE FROM AuditTrail a WHERE a.synced = true AND a.timestamp < ?1")
    void deleteSyncedRecordsOlderThan(LocalDateTime date);
} 