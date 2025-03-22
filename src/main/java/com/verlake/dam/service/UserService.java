package com.verlake.dam.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.verlake.dam.entity.User;
import com.verlake.dam.repository.UserRepository;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;


    public User findOrCreateUser(String email) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(email);
                    return userRepository.save(newUser);
                });
    }

    public User saveUser(User user) {
        return userRepository.save(user);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    return null;
                });
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseGet(() -> {
                    return null;
                });
    }

    public List<User> getAdminRoleUsers() {
        return userRepository.findAll().stream()
                .filter(user -> user.getRoles().stream()
                        .anyMatch(role -> "ADMIN".equalsIgnoreCase(role.getName())))
                .collect(Collectors.toList());

        
    }
}
