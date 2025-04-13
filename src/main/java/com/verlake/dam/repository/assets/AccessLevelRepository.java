package com.verlake.dam.repository.assets;

import com.verlake.dam.entity.assets.AccessLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccessLevelRepository extends JpaRepository<AccessLevel, Long> {
    
    List<AccessLevel> findByAssetTypeAndDatabaseType(String assetType, String databaseType);
    
    @Query("SELECT DISTINCT a.object FROM AccessLevel a WHERE a.assetType = :assetType AND a.databaseType = :databaseType AND a.object IS NOT NULL")
    List<String> findDistinctObjectsByAssetTypeAndDatabaseType(
        @Param("assetType") String assetType, 
        @Param("databaseType") String databaseType
    );
    
    @Query("SELECT a.templates FROM AccessLevel a WHERE a.assetType = :assetType AND a.databaseType = :databaseType AND a.templates IS NOT NULL")
    List<String> findTemplatesByAssetTypeAndDatabaseType(
        @Param("assetType") String assetType, 
        @Param("databaseType") String databaseType
    );
    
    List<AccessLevel> findByAssetTypeAndDatabaseTypeAndObject(
        String assetType, 
        String databaseType,
        String object
    );
    
    AccessLevel findByAssetTypeAndDatabaseTypeAndTemplates(
        String assetType, 
        String databaseType,
        String templates
    );

    @Query("SELECT a FROM AccessLevel a WHERE a.assetType = :assetType AND a.databaseType = :databaseType AND a.templates = 'FETCH ACCESS'")
    AccessLevel findFetchAccessTemplate(
        @Param("assetType") String assetType, 
        @Param("databaseType") String databaseType
    );
} 