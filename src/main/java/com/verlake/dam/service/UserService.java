package com.verlake.dam.service;

import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.user.dto.UserFilter;
import com.verlake.dam.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Object getAllUsers(UserFilter filter) {
        Pageable pageable = filter.toPageRequest(Sort.by(Sort.Direction.DESC, "id"));

        List<User> users;
        if (pageable.isUnpaged()) {
            users = userRepository.findAll(filter.toSpecification(), Sort.by(Sort.Direction.DESC, "id"));
        } else {
            Page<User> userPage = userRepository.findAll(filter.toSpecification(), pageable);
            users = userPage.getContent();
        }
        return pageable.isUnpaged() ? users : new PageImpl<>(users, pageable, users.size());
    }

    @Transactional
    public User setApproverForUser(Long userId, Long approverId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approver not found"));
        user.setApprover(approver.toApproverDTO());
        userRepository.save(user);
        return user;
    }

    @Transactional
    public User unsetApproverForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        user.setApprover(null);
        userRepository.save(user);
        return user;
    }

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
                .toList();
    }
}
