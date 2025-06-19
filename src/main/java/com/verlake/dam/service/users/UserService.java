package com.verlake.dam.service.users;

import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.user.dto.UserFilter;
import com.verlake.dam.repository.UserRepository;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Pattern;

@Service
@Slf4j
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Can't find the user need to set an approver for."));

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approver not found"));
        user.setApprover(approver.toApproverDTO());
        userRepository.save(user);
        return user;
    }

    @Transactional
    public User unsetApproverForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Can't find the user need to unset an approver for."));

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

    public User getCurrentUser() {
        String email = CommonUtils.getEmailFromSession();

        return userRepository.findByEmail(email).orElseThrow(() -> new AccessDeniedException("Current User not found"));
    }

    /**
     * Validates password complexity according to the following requirements:
     * - Minimum 12 characters
     * - At least one uppercase letter (A–Z)
     * - At least one lowercase letter (a–z)
     * - At least one digit (0–9)
     * - At least one special character
     */
    public void checkPasswordComplexity(String password) {
        if (password == null || password.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password cannot be empty");
        }

        if (password.length() < 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Password must be at least 12 characters long");
        }

        if (!Pattern.compile("[A-Z]").matcher(password).find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Password must contain at least one uppercase letter (A–Z)");
        }

        if (!Pattern.compile("[a-z]").matcher(password).find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Password must contain at least one lowercase letter (a–z)");
        }

        if (!Pattern.compile("\\d").matcher(password).find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Password must contain at least one digit (0–9)");
        }

        if (!Pattern.compile("[!@#$%^&*()\\-_=+\\[\\]{}|;:'\",.<>/?]").matcher(password).find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Password must contain at least one special character (!@#$%^&*()-_=+[]{}|;:'\",.<>/?)");
        }
    }

    /**
     * Generates a complex password that meets all complexity requirements
     */
    public String generateComplexPassword() {
        SecureRandom random = new SecureRandom();
        String upperCase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowerCase = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String specialChars = "!@#$%^&*()-_=+[]{}|;:'\",.<>/?";
        String allChars = upperCase + lowerCase + digits + specialChars;

        StringBuilder password = new StringBuilder();
        
        // Ensure at least one character from each required category
        password.append(upperCase.charAt(random.nextInt(upperCase.length())));
        password.append(lowerCase.charAt(random.nextInt(lowerCase.length())));
        password.append(digits.charAt(random.nextInt(digits.length())));
        password.append(specialChars.charAt(random.nextInt(specialChars.length())));

        // Fill the rest with random characters to reach 12+ characters
        for (int i = 4; i < 12; i++) {
            password.append(allChars.charAt(random.nextInt(allChars.length())));
        }

        // Shuffle the password to avoid predictable patterns
        char[] passwordArray = password.toString().toCharArray();
        for (int i = passwordArray.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = passwordArray[i];
            passwordArray[i] = passwordArray[j];
            passwordArray[j] = temp;
        }

        return new String(passwordArray);
    }

    /**
     * Get users that have the specified user as their approver
     * @param approver The approver user
     * @return List of users that have this approver
     */
    public List<User> getUsersByApprover(User approver) {
        return userRepository.findByApprover(approver.getId());
    }

    /**
     * Generates a secure temporary password that meets complexity requirements
     */
    public String generateSecureTemporaryPassword() {
        // Use a secure random generator
        java.security.SecureRandom random = new java.security.SecureRandom();
        
        // Define character sets
        String lowercase = Constants.PSSWD_LOWERCASE;
        String uppercase = Constants.PSSWD_UPPERCASE;
        String digits = Constants.PSSWD_DIGITS;
        String specials = Constants.PSSWD_SPECIALS;
        String allChars = lowercase + uppercase + digits + specials;
        
        StringBuilder password = new StringBuilder();
        
        // Ensure at least one character from each required set
        password.append(lowercase.charAt(random.nextInt(lowercase.length())));
        password.append(uppercase.charAt(random.nextInt(uppercase.length())));
        password.append(digits.charAt(random.nextInt(digits.length())));
        password.append(specials.charAt(random.nextInt(specials.length())));
        
        // Fill the rest randomly (minimum 12 characters total)
        for (int i = 4; i < 16; i++) {
            password.append(allChars.charAt(random.nextInt(allChars.length())));
        }
        
        // Shuffle the password to avoid predictable patterns
        List<Character> passwordChars = new ArrayList<>();
        for (char c : password.toString().toCharArray()) {
            passwordChars.add(c);
        }
        Collections.shuffle(passwordChars, random);
        
        StringBuilder shuffledPassword = new StringBuilder();
        for (char c : passwordChars) {
            shuffledPassword.append(c);
        }
        
        return shuffledPassword.toString();
    }

    /**
     * Activates a user account
     * @param user The user to activate
     * @return The activated user
     */
    public User activateUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        
        user.setIsActive(true);
        user.setInviteCode(null);
        user.setInviteEmail(null);
        
        User savedUser = userRepository.save(user);
        log.info("User {} has been activated", user.getEmail());
        
        return savedUser;
    }

    /**
     * Deactivates a user account
     * @param user The user to deactivate
     * @return The deactivated user
     */
    public User deactivateUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        
        user.setIsActive(false);
        User savedUser = userRepository.save(user);
        log.info("User {} has been deactivated", user.getEmail());
        
        return savedUser;
    }

    /**
     * Checks if a user is active
     * @param user The user to check
     * @return true if the user is active, false otherwise
     */
    public boolean isUserActive(User user) {
        if (user == null) {
            return false;
        }
        return user.getIsActive() != null && user.getIsActive();
    }

    /**
     * Activates user when they complete invitation process (e.g., setting password)
     * @param user The user to activate
     * @return The activated user
     */
    public User completeUserActivation(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        
        // Only activate if user is currently inactive
        if (!isUserActive(user)) {
            return activateUser(user);
        }
        
        return user;
    }

}
