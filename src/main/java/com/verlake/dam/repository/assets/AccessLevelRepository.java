package com.verlake.dam.repository.assets;

import com.verlake.dam.entity.assets.AccessLevel;
import com.verlake.dam.enums.AssetType;
import com.verlake.dam.enums.DatabaseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccessLevelRepository extends JpaRepository<AccessLevel, Long> {
    
    List<AccessLevel> findByAssetTypeAndDatabaseType(AssetType assetType, DatabaseType databaseType);
    
    @Query("SELECT DISTINCT a.object FROM AccessLevel a WHERE a.assetType = :assetType AND a.databaseType = :databaseType AND a.object IS NOT NULL")
    List<String> findDistinctObjectsByAssetTypeAndDatabaseType(
        @Param("assetType") AssetType assetType, 
        @Param("databaseType") DatabaseType databaseType
    );
    
    @Query("SELECT a.templates FROM AccessLevel a WHERE a.assetType = :assetType AND a.databaseType = :databaseType AND a.templates IS NOT NULL")
    List<String> findTemplatesByAssetTypeAndDatabaseType(
        @Param("assetType") AssetType assetType, 
        @Param("databaseType") DatabaseType databaseType
    );
    
    List<AccessLevel> findByAssetTypeAndDatabaseTypeAndObject(
        AssetType assetType, 
        DatabaseType databaseType,
        String object
    );
    
    AccessLevel findByAssetTypeAndDatabaseTypeAndTemplates(
        AssetType assetType, 
        DatabaseType databaseType,
        String templates
    );

    AccessLevel findByAssetTypeAndDatabaseTypeAndObjectAndTemplates(
        AssetType assetType,
        DatabaseType databaseType,
        String object,
        String templates
    );

    @Query("SELECT a FROM AccessLevel a WHERE a.assetType = :assetType AND a.databaseType = :databaseType AND a.templates = 'FETCH ACCESS'")
    AccessLevel findFetchAccessTemplate(
        @Param("assetType") AssetType assetType, 
        @Param("databaseType") DatabaseType databaseType
    );
} 