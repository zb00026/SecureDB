package com.verlake.dam.service;

import com.verlake.dam.entity.User;
import com.verlake.dam.repository.UserRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KeycloakUserMapper {
    private final UserRepository userRepository;

    public KeycloakUserMapper(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User createOrUpdateUser(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        String keycloakId = jwt.getSubject();
        
        return userRepository.findByKeycloakId(keycloakId)
                .map(user -> updateExistingUser(user, jwt))
                .orElseGet(() -> createNewUser(jwt));
    }

    private User updateExistingUser(User user, Jwt jwt) {
        user.setEmail(jwt.getClaimAsString("email"));
        user.setName(jwt.getClaimAsString("name"));
        // Update any other relevant fields
        return userRepository.save(user);
    }

    private User createNewUser(Jwt jwt) {
        User user = new User();
        user.setKeycloakId(jwt.getSubject());
        user.setEmail(jwt.getClaimAsString("email"));
        user.setName(jwt.getClaimAsString("name"));
        
        // Set additional fields if available in JWT
        if (jwt.hasClaim("given_name")) {
            user.setName(jwt.getClaimAsString("given_name"));
        }
        
        return userRepository.save(user);
    }
} 