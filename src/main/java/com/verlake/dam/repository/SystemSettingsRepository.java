package com.verlake.dam.repository;

import com.verlake.dam.entity.SystemSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemSettingsRepository extends JpaRepository<SystemSettings, Long> {
    
    /**
     * Find system setting by key
     */
    Optional<SystemSettings> findBySettingKey(String settingKey);
    
    /**
     * Check if setting exists by key
     */
    boolean existsBySettingKey(String settingKey);
}
