package com.verlake.dam.repository.assets;

import com.verlake.dam.entity.assets.AccessLevelObject;
import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface AccessLevelObjectRepository extends JpaRepository<AccessLevelObject, Long> {
    // Find by access request
    List<AccessLevelObject> findByAccessRequest(AccessRequest accessRequest);

    // Find by requestor
    List<AccessLevelObject> findByRequestor(User requestor);

    // Find by access request ID
    List<AccessLevelObject> findByAccessRequestId(Long accessRequestId);

    // Find by object name (exact match)
    List<AccessLevelObject> findByObjectName(String objectName);

    // Find by object name containing pattern
    List<AccessLevelObject> findByObjectNameContaining(String pattern);

    // Find by access level ID
    List<AccessLevelObject> findByAccessLevelId(Long accessLevelId);

    // Delete by access request
    @Modifying
    @Transactional
    @Query("DELETE FROM AccessLevelObject a WHERE a.accessRequest = :request")
    void deleteByAccessRequest(@Param("request") AccessRequest request);

    // Check if exists by object name and access request
    boolean existsByObjectNameAndAccessRequest(String objectName, AccessRequest accessRequest);
}