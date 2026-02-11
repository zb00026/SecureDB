package com.verlake.dam.repository;

import com.verlake.dam.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.email = :email AND (u.deleted IS NULL OR u.deleted = false)")
    boolean existsByEmail(@Param("email") String email);
    
    @Query("SELECT u FROM User u WHERE u.email = :email AND (u.deleted IS NULL OR u.deleted = false)")
    Optional<User> findByEmail(@Param("email") String email);
    
    // Find user by email including deleted users (for restoring deleted users)
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailIncludingDeleted(@Param("email") String email);
    
    @Query("SELECT u FROM User u WHERE u.approverId = :approverId AND (u.deleted IS NULL OR u.deleted = false)")
    List<User> findByApprover(@Param("approverId") Long approverId);
    
    // Check for duplicate first names (excluding deleted users)
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE LOWER(u.firstName) = LOWER(:firstName) AND (u.deleted IS NULL OR u.deleted = false)")
    boolean existsByFirstNameIgnoreCase(@Param("firstName") String firstName);
    
    // Check for duplicate last names (excluding deleted users)
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE LOWER(u.lastName) = LOWER(:lastName) AND (u.deleted IS NULL OR u.deleted = false)")
    boolean existsByLastNameIgnoreCase(@Param("lastName") String lastName);
    
    // Check for duplicate first name and last name combination (excluding deleted users)
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE LOWER(u.firstName) = LOWER(:firstName) AND LOWER(u.lastName) = LOWER(:lastName) AND (u.deleted IS NULL OR u.deleted = false)")
    boolean existsByFirstNameIgnoreCaseAndLastNameIgnoreCase(@Param("firstName") String firstName, @Param("lastName") String lastName);
    
    // Find users by first name and last name combination (for validation, excluding deleted users)
    @Query("SELECT u FROM User u WHERE LOWER(u.firstName) = LOWER(:firstName) AND LOWER(u.lastName) = LOWER(:lastName) AND (u.deleted IS NULL OR u.deleted = false)")
    List<User> findByFirstNameAndLastNameIgnoreCase(@Param("firstName") String firstName, @Param("lastName") String lastName);
    
    // Find users by first name only (for validation, excluding deleted users)
    @Query("SELECT u FROM User u WHERE LOWER(u.firstName) = LOWER(:firstName) AND (u.deleted IS NULL OR u.deleted = false)")
    List<User> findByFirstNameIgnoreCase(@Param("firstName") String firstName);
    
    // Find users by last name only (for validation, excluding deleted users)
    @Query("SELECT u FROM User u WHERE LOWER(u.lastName) = LOWER(:lastName) AND (u.deleted IS NULL OR u.deleted = false)")
    List<User> findByLastNameIgnoreCase(@Param("lastName") String lastName);
}