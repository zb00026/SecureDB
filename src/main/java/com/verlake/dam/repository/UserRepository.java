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
    
    // Check for duplicate first names
    boolean existsByFirstNameIgnoreCase(String firstName);
    
    // Check for duplicate last names
    boolean existsByLastNameIgnoreCase(String lastName);
    
    // Check for duplicate first name and last name combination
    boolean existsByFirstNameIgnoreCaseAndLastNameIgnoreCase(String firstName, String lastName);
    
    // Find users by first name and last name combination (for validation)
    @Query("SELECT u FROM User u WHERE LOWER(u.firstName) = LOWER(:firstName) AND LOWER(u.lastName) = LOWER(:lastName)")
    List<User> findByFirstNameAndLastNameIgnoreCase(@Param("firstName") String firstName, @Param("lastName") String lastName);
    
    // Find users by first name only (for validation)
    @Query("SELECT u FROM User u WHERE LOWER(u.firstName) = LOWER(:firstName)")
    List<User> findByFirstNameIgnoreCase(@Param("firstName") String firstName);
    
    // Find users by last name only (for validation)
    @Query("SELECT u FROM User u WHERE LOWER(u.lastName) = LOWER(:lastName)")
    List<User> findByLastNameIgnoreCase(@Param("lastName") String lastName);
}