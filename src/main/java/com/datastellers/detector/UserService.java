package com.datastellers.detector;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service class for handling user registration and login operations.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Registers a new user after validating all input fields.
     */
    public User registerUser(String fullName, String email, String password, String confirmPassword) {
        // 1. Validate full name
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Full name is required.");
        }

        // 2. Validate email
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email address is required.");
        }
        String cleanEmail = email.trim().toLowerCase();
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            throw new IllegalArgumentException("Please enter a valid email address.");
        }

        // 3. Validate password
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long.");
        }

        // 4. Validate password confirmation
        if (!password.equals(confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match.");
        }

        // 5. Check if email already exists
        if (userRepository.existsByEmail(cleanEmail)) {
            throw new IllegalArgumentException("An account with this email address already exists. Please log in.");
        }

        // 6. Hash password and save user
        String passwordHash = PasswordUtil.hashPassword(password);
        User user = new User(fullName.trim(), cleanEmail, passwordHash, LocalDateTime.now());
        return userRepository.save(user);
    }

    /**
     * Authenticates a user by email and plain password.
     */
    public User authenticate(String email, String password) {
        if (email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Email and password are required.");
        }

        String cleanEmail = email.trim().toLowerCase();
        Optional<User> optionalUser = userRepository.findByEmail(cleanEmail);

        if (optionalUser.isEmpty()) {
            throw new IllegalArgumentException("Invalid email or password.");
        }

        User user = optionalUser.get();
        boolean passwordMatches = PasswordUtil.checkPassword(password, user.getPasswordHash());

        if (!passwordMatches) {
            throw new IllegalArgumentException("Invalid email or password.");
        }

        return user;
    }

    /**
     * Retrieves a user by their database ID.
     */
    public Optional<User> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return userRepository.findById(id);
    }
}
