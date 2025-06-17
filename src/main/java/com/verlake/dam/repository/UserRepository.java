package com.verlake.dam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.verlake.dam.entity.user.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
    
    @Query("SELECT u FROM User u WHERE u.approverId = :approverId")
    List<User> findByApprover(@Param("approverId") Long approverId);
}