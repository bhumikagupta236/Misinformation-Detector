package com.datastellers.detector;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller handling user authentication (Registration, Login, Logout, Session check).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final String SESSION_USER_ID = "TRUST_GUIDE_USER_ID";
    public static final String SESSION_USER_NAME = "TRUST_GUIDE_USER_NAME";
    public static final String SESSION_USER_EMAIL = "TRUST_GUIDE_USER_EMAIL";

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * POST /api/auth/register
     * Registers a new user account.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        if (body == null) {
            return buildErrorResponse("Request body cannot be empty.", HttpStatus.BAD_REQUEST);
        }

        String fullName = body.get("fullName");
        String email = body.get("email");
        String password = body.get("password");
        String confirmPassword = body.get("confirmPassword");

        try {
            User newUser = userService.registerUser(fullName, email, password, confirmPassword);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Registration successful! You can now log in.");
            response.put("userId", newUser.getId());
            response.put("email", newUser.getEmail());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException ex) {
            return buildErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception ex) {
            return buildErrorResponse("Registration failed due to a server error. Please try again.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * POST /api/auth/login
     * Logs in a user and establishes an HTTP session.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpSession session) {
        if (body == null) {
            return buildErrorResponse("Request body cannot be empty.", HttpStatus.BAD_REQUEST);
        }

        String email = body.get("email");
        String password = body.get("password");

        try {
            User user = userService.authenticate(email, password);

            // Store user identity in the HTTP session
            session.setAttribute(SESSION_USER_ID, user.getId());
            session.setAttribute(SESSION_USER_NAME, user.getFullName());
            session.setAttribute(SESSION_USER_EMAIL, user.getEmail());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Login successful!");
            response.put("user", buildUserSummary(user));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return buildErrorResponse(ex.getMessage(), HttpStatus.UNAUTHORIZED);
        } catch (Exception ex) {
            return buildErrorResponse("Login failed due to a server error. Please try again.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * GET /api/auth/current-user
     * Returns the currently logged in user from session.
     */
    @GetMapping("/current-user")
    public ResponseEntity<?> getCurrentUser(HttpSession session) {
        Long userId = (Long) session.getAttribute(SESSION_USER_ID);
        if (userId == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("loggedIn", false);
            return ResponseEntity.ok(response);
        }

        String userName = (String) session.getAttribute(SESSION_USER_NAME);
        String userEmail = (String) session.getAttribute(SESSION_USER_EMAIL);

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", userId);
        userMap.put("fullName", userName);
        userMap.put("email", userEmail);

        Map<String, Object> response = new HashMap<>();
        response.put("loggedIn", true);
        response.put("user", userMap);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/auth/logout
     * Clears the current user session.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        Map<String, String> response = new HashMap<>();
        response.put("message", "Logged out successfully.");
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> buildUserSummary(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("fullName", user.getFullName());
        map.put("email", user.getEmail());
        return map;
    }

    private ResponseEntity<Map<String, String>> buildErrorResponse(String message, HttpStatus status) {
        Map<String, String> errorMap = new HashMap<>();
        errorMap.put("error", message);
        return ResponseEntity.status(status).body(errorMap);
    }
}
