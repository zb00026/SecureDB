package com.verlake.dam.repository.terminal;

import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.terminal.TerminalSessionRecording;
import com.verlake.dam.entity.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TerminalSessionRecordingRepository extends JpaRepository<TerminalSessionRecording, Long>, JpaSpecificationExecutor<TerminalSessionRecording> {
    
    /**
     * Find session recording by session ID
     */
    Optional<TerminalSessionRecording> findBySessionId(String sessionId);
    
    /**
     * Find all active sessions
     */
    List<TerminalSessionRecording> findByIsActiveTrue();
    
    /**
     * Find all active sessions with sorting
     */
    List<TerminalSessionRecording> findByIsActiveTrue(Sort sort);
    
    /**
     * Find sessions by user
     */
    Page<TerminalSessionRecording> findByUserOrderBySessionStartDesc(User user, Pageable pageable);
    
    /**
     * Find sessions by asset
     */
    Page<TerminalSessionRecording> findByAssetOrderBySessionStartDesc(Asset asset, Pageable pageable);
    
    /**
     * Find sessions by user and asset
     */
    Page<TerminalSessionRecording> findByUserAndAssetOrderBySessionStartDesc(User user, Asset asset, Pageable pageable);
    
    /**
     * Find sessions within date range
     */
    @Query("SELECT tsr FROM TerminalSessionRecording tsr WHERE tsr.sessionStart >= :startDate AND tsr.sessionStart <= :endDate ORDER BY tsr.sessionStart DESC")
    Page<TerminalSessionRecording> findBySessionStartBetween(
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate, 
        Pageable pageable
    );
    
    /**
     * Find sessions by username (SSH username)
     */
    Page<TerminalSessionRecording> findByUsernameOrderBySessionStartDesc(String username, Pageable pageable);
    
    /**
     * Find long-running sessions (active for more than specified minutes)
     */
    @Query("SELECT tsr FROM TerminalSessionRecording tsr WHERE tsr.isActive = true AND tsr.sessionStart < :cutoffTime")
    List<TerminalSessionRecording> findLongRunningSessions(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * Count active sessions by user
     */
    @Query("SELECT COUNT(tsr) FROM TerminalSessionRecording tsr WHERE tsr.user = :user AND tsr.isActive = true")
    long countActiveSessionsByUser(@Param("user") User user);
    
    /**
     * Count active sessions by asset
     */
    @Query("SELECT COUNT(tsr) FROM TerminalSessionRecording tsr WHERE tsr.asset = :asset AND tsr.isActive = true")
    long countActiveSessionsByAsset(@Param("asset") Asset asset);
    
    /**
     * Find sessions with high command count (potential suspicious activity)
     */
    @Query("SELECT tsr FROM TerminalSessionRecording tsr WHERE tsr.commandCount > :threshold ORDER BY tsr.commandCount DESC")
    List<TerminalSessionRecording> findSessionsWithHighCommandCount(@Param("threshold") Integer threshold);
    
    /**
     * Get session statistics for a user within date range
     */
    @Query("SELECT COUNT(tsr), AVG(tsr.durationSeconds), SUM(tsr.commandCount) FROM TerminalSessionRecording tsr WHERE tsr.user = :user AND tsr.sessionStart >= :startDate AND tsr.sessionStart <= :endDate")
    Object[] getSessionStatistics(@Param("user") User user, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}
