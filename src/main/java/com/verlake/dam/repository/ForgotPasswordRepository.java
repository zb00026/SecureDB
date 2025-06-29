package com.verlake.dam.repository;

import com.verlake.dam.entity.user.ForgotPassword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ForgotPasswordRepository extends JpaRepository<ForgotPassword, Long> {
    
    Optional<ForgotPassword> findByTokenCode(String tokenCode);
    
    List<ForgotPassword> findByEmailOrderBySentDateDesc(String email);
    
    List<ForgotPassword> findByUserIdOrderBySentDateDesc(Long userId);
    
    @Query("SELECT fp FROM ForgotPassword fp WHERE fp.email = :email AND fp.expirationDate > :now AND fp.isUsed = false")
    List<ForgotPassword> findActiveByEmail(@Param("email") String email, @Param("now") LocalDateTime now);
    
    @Query("SELECT fp FROM ForgotPassword fp WHERE fp.userId = :userId AND fp.expirationDate > :now AND fp.isUsed = false")
    List<ForgotPassword> findActiveByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);
    
    @Query("SELECT fp FROM ForgotPassword fp WHERE fp.expirationDate < :now AND fp.isUsed = false")
    List<ForgotPassword> findExpiredTokens(@Param("now") LocalDateTime now);
} 