package com.verlake.dam.service.auth;

import com.verlake.dam.entity.user.User;
import com.verlake.dam.exception.JwtTokenException;
import com.verlake.dam.utils.Constants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class JwtPasswordResetService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.password-reset.expiration-hours:24}")
    private int expirationHours;

    /**
     * Generate a JWT token for password reset
     */
    public String generatePasswordResetToken(User user) {
        return generatePasswordResetToken(user.getEmail(), user.getId());
    }

    /**
     * Generate a JWT token for password reset with email and userId
     */
    public String generatePasswordResetToken(String email, Long userId) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            
            Date expiration = Date.from(
                LocalDateTime.now().plusHours(expirationHours)
                    .atZone(ZoneId.systemDefault()).toInstant()
            );

            Map<String, Object> claims = new HashMap<>();
            claims.put(Constants.JWT_CLAIM_EMAIL, email);
            claims.put(Constants.JWT_CLAIM_USER_ID, userId);
            claims.put(Constants.JWT_CLAIM_TYPE, Constants.JWT_CLAIM_TYPE_PASSWORD_RESET);

            return Jwts.builder()
                .setSubject(Constants.JWT_SUBJECT_PASSWORD_RESET)
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(expiration)
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        } catch (Exception e) {
            log.error("Error generating password reset token for user: {}", email, e);
            throw new JwtTokenException(Constants.getMessage("jwt.error.generation.failed"), e);
        }
    }

    /**
     * Validate and extract user information from password reset token
     */
    public Map<String, Object> validatePasswordResetToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            // Validate token type
            String tokenType = claims.get(Constants.JWT_CLAIM_TYPE, String.class);
            if (!Constants.JWT_CLAIM_TYPE_PASSWORD_RESET.equals(tokenType)) {
                throw new JwtException(Constants.getMessage("jwt.error.invalid.token.type"));
            }

            // Check if token is expired
            if (claims.getExpiration().before(new Date())) {
                throw new JwtException(Constants.getMessage("jwt.error.token.expired"));
            }

            Map<String, Object> result = new HashMap<>();
            result.put(Constants.JWT_CLAIM_EMAIL, claims.get(Constants.JWT_CLAIM_EMAIL, String.class));
            result.put(Constants.JWT_CLAIM_USER_ID, claims.get(Constants.JWT_CLAIM_USER_ID, Long.class));
            result.put("expiration", claims.getExpiration());

            log.info("Password reset token validated successfully for user: {}", result.get(Constants.JWT_CLAIM_EMAIL));
            return result;

        } catch (JwtException e) {
            log.warn("Invalid password reset token: {}", e.getMessage());
            throw new JwtTokenException(Constants.getMessage("jwt.error.invalid.token"));
        } catch (Exception e) {
            log.error("Error validating password reset token", e);
            throw new JwtTokenException(Constants.getMessage("jwt.error.validation.failed"), e);
        }
    }

    /**
     * Check if token is expired without throwing exception
     */
    public boolean isTokenExpired(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true; // Consider invalid tokens as expired
        }
    }

    /**
     * Get token expiration time
     */
    public Date getTokenExpiration(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            return claims.getExpiration();
        } catch (Exception e) {
            return null;
        }
    }
} 