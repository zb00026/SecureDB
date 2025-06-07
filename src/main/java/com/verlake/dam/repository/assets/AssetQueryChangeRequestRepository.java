package com.verlake.dam.repository.assets;

import com.verlake.dam.entity.assets.AssetQueryChangeRequest;
import com.verlake.dam.entity.assets.Asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetQueryChangeRequestRepository extends JpaRepository<AssetQueryChangeRequest, Long> {
    
    // Find by ticket reference
    Optional<AssetQueryChangeRequest> findByTicketReference(String ticketReference);
    
    // Find by ticket reference containing pattern
    List<AssetQueryChangeRequest> findByTicketReferenceContaining(String pattern);
    
    // Find by change description containing pattern
    List<AssetQueryChangeRequest> findByChangeDescriptionContaining(String pattern);
    
    // Find by query containing pattern
    List<AssetQueryChangeRequest> findByQueryContaining(String pattern);

    // Find by asset
    List<AssetQueryChangeRequest> findByAsset(Asset asset);

    // Find by assets
    List<AssetQueryChangeRequest> findByAssetIn(List<Asset> assets);
    
    // Check if exists by ticket reference
    boolean existsByTicketReference(String ticketReference);
    
    // Custom query to search across multiple fields
    @Query("SELECT a FROM AssetQueryChangeRequest a WHERE " +
           "(:ticketReference IS NULL OR LOWER(a.ticketReference) LIKE LOWER(CONCAT('%', :ticketReference, '%'))) AND " +
           "(:description IS NULL OR LOWER(a.changeDescription) LIKE LOWER(CONCAT('%', :description, '%'))) AND " +
           "(:query IS NULL OR LOWER(a.query) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<AssetQueryChangeRequest> searchByFields(
        @Param("ticketReference") String ticketReference,
        @Param("description") String description,
        @Param("query") String query
    );
} 