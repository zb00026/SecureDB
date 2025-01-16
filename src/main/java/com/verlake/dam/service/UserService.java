package com.verlake.dam.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.verlake.dam.entity.User;
import com.verlake.dam.repository.UserRepository;

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

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    return null;
                });
    }
}
