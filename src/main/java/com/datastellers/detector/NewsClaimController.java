package com.datastellers.detector;

import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST Controller that handles HTTP requests for claim analysis, history, and analytics.
 */
@RestController
@RequestMapping("/api/claims")
public class NewsClaimController {

    private static final int MAX_CLAIM_LENGTH = 3000;

    private final NewsClaimService newsClaimService;

    public NewsClaimController(NewsClaimService newsClaimService) {
        this.newsClaimService = newsClaimService;
    }

    /**
     * POST /api/claims/analyze
     * Validates a news claim, analyzes it, associates it with logged-in user if available,
     * saves it in the database, and returns the saved record.
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeClaim(
            @RequestBody(required = false) Map<String, String> requestBody,
            HttpSession session
    ) {
        if (requestBody == null || !requestBody.containsKey("claimText")) {
            return buildErrorResponse("Please enter a news claim or headline before analyzing.", HttpStatus.BAD_REQUEST);
        }

        String claimText = requestBody.get("claimText");
        if (claimText == null || claimText.trim().isEmpty()) {
            return buildErrorResponse("The claim input cannot be empty. Please type or paste a news claim.", HttpStatus.BAD_REQUEST);
        }

        if (claimText.trim().length() > MAX_CLAIM_LENGTH) {
            return buildErrorResponse(
                    "The entered text is too long (" + claimText.trim().length() + " characters). Please keep claims under " + MAX_CLAIM_LENGTH + " characters.",
                    HttpStatus.BAD_REQUEST
            );
        }

        Long userId = (session != null) ? (Long) session.getAttribute(AuthController.SESSION_USER_ID) : null;
        NewsClaim savedClaim = newsClaimService.analyzeAndSaveClaim(claimText, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedClaim);
    }

    /**
     * GET /api/claims
     * Retrieves saved claims from database, newest first. If user is logged in, filters by user.
     */
    @GetMapping
    public ResponseEntity<List<NewsClaim>> getAllClaims(HttpSession session) {
        Long userId = (session != null) ? (Long) session.getAttribute(AuthController.SESSION_USER_ID) : null;
        List<NewsClaim> historyList = newsClaimService.getAllHistory(userId);
        return ResponseEntity.ok(historyList);
    }

    /**
     * GET /api/claims/{id}
     * Retrieves a single analyzed claim record by its ID. Used by the Result Display Page.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getClaimById(@PathVariable("id") Long id) {
        if (id == null || id <= 0) {
            return buildErrorResponse("Invalid record ID provided.", HttpStatus.BAD_REQUEST);
        }

        Optional<NewsClaim> claimOptional = newsClaimService.getClaimById(id);
        if (claimOptional.isEmpty()) {
            return buildErrorResponse("Claim record with ID " + id + " was not found.", HttpStatus.NOT_FOUND);
        }

        return ResponseEntity.ok(claimOptional.get());
    }

    /**
     * DELETE /api/claims/{id}
     * Deletes a specific claim record from the database by its ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteClaimById(@PathVariable("id") Long id, HttpSession session) {
        if (id == null || id <= 0) {
            return buildErrorResponse("Invalid record ID provided.", HttpStatus.BAD_REQUEST);
        }

        Long userId = (session != null) ? (Long) session.getAttribute(AuthController.SESSION_USER_ID) : null;
        boolean deleted = newsClaimService.deleteClaimById(id, userId);
        if (!deleted) {
            return buildErrorResponse("History record with ID " + id + " was not found or could not be deleted.", HttpStatus.NOT_FOUND);
        }

        Map<String, String> response = new HashMap<>();
        response.put("message", "History record deleted successfully.");
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/claims
     * Clears saved claim records from the database.
     */
    @DeleteMapping
    public ResponseEntity<?> clearAllClaims(HttpSession session) {
        Long userId = (session != null) ? (Long) session.getAttribute(AuthController.SESSION_USER_ID) : null;
        newsClaimService.clearAllHistory(userId);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Analysis history has been cleared.");
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/claims/analytics
     * Computes and returns real statistics and chart metrics from the database records.
     */
    @GetMapping("/analytics")
    public ResponseEntity<?> getAnalytics(HttpSession session) {
        Long userId = (session != null) ? (Long) session.getAttribute(AuthController.SESSION_USER_ID) : null;
        Map<String, Object> stats = newsClaimService.getAnalytics(userId);
        return ResponseEntity.ok(stats);
    }

    /**
     * Handles invalid ID formats in the URL (for example: /api/claims/abc).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleInvalidIdFormat(MethodArgumentTypeMismatchException ex) {
        return buildErrorResponse("Invalid record ID format. The ID must be a valid number.", HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles database query failures with a friendly message.
     */
    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    public ResponseEntity<Map<String, String>> handleDatabaseException(Exception ex) {
        return buildErrorResponse(
                "Database error: Unable to complete the operation. Please verify database connectivity.",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    /**
     * Handles any other unexpected server errors without exposing raw Java stack traces.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        return buildErrorResponse(
                "An unexpected server error occurred while processing your request. Please try again.",
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    private ResponseEntity<Map<String, String>> buildErrorResponse(String message, HttpStatus status) {
        Map<String, String> errorMap = new HashMap<>();
        errorMap.put("error", message);
        return ResponseEntity.status(status).body(errorMap);
    }
}
