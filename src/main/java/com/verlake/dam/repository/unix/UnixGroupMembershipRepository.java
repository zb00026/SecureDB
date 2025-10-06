package com.verlake.dam.repository.unix;

import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.unix.UnixGroupMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnixGroupMembershipRepository extends JpaRepository<UnixGroupMembership, Long> {
    
    /**
     * Find all group memberships for a specific access request
     */
    List<UnixGroupMembership> findByAccessRequest(AccessRequest request);
    
    /**
     * Delete all memberships for a specific access request
     */
    void deleteByAccessRequest(AccessRequest request);
}

