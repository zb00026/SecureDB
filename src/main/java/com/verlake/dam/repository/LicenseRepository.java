package com.verlake.dam.repository;

import com.verlake.dam.entity.License;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface LicenseRepository extends JpaRepository<License, Long> {
    
    /**
     * Find the currently active license
     */
    Optional<License> findByIsActiveTrue();
    
    /**
     * Deactivate all existing licenses
     */
    @Modifying
    @Transactional
    @Query("UPDATE License l SET l.isActive = false")
    void deactivateAllLicenses();
    
    /**
     * Check if any active license exists
     */
    boolean existsByIsActiveTrue();
    
    /**
     * Count total number of licenses (for reference)
     */
    long countByIsActiveTrue();
} 