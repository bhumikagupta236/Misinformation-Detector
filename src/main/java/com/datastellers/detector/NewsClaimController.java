package com.datastellers.detector;

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

/**
 * REST Controller that handles HTTP requests from the frontend webpage.
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
     * Validates a news claim, analyzes it, saves it in MySQL, and returns the saved record.
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeClaim(@RequestBody(required = false) Map<String, String> requestBody) {
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

        NewsClaim savedClaim = newsClaimService.analyzeAndSaveClaim(claimText);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedClaim);
    }

    /**
     * GET /api/claims
     * Retrieves all previously analyzed claims from MySQL, newest first.
     */
    @GetMapping
    public ResponseEntity<List<NewsClaim>> getAllClaims() {
        List<NewsClaim> historyList = newsClaimService.getAllHistory();
        return ResponseEntity.ok(historyList);
    }

    /**
     * DELETE /api/claims/{id}
     * Deletes a specific claim record from MySQL by its ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteClaimById(@PathVariable("id") Long id) {
        if (id == null || id <= 0) {
            return buildErrorResponse("Invalid record ID provided.", HttpStatus.BAD_REQUEST);
        }

        boolean deleted = newsClaimService.deleteClaimById(id);
        if (!deleted) {
            return buildErrorResponse("History record with ID " + id + " was not found.", HttpStatus.NOT_FOUND);
        }

        Map<String, String> response = new HashMap<>();
        response.put("message", "History record deleted successfully.");
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/claims
     * Clears all saved claim records from MySQL.
     */
    @DeleteMapping
    public ResponseEntity<?> clearAllClaims() {
        newsClaimService.clearAllHistory();

        Map<String, String> response = new HashMap<>();
        response.put("message", "All analysis history has been cleared.");
        return ResponseEntity.ok(response);
    }

    /**
     * Handles invalid ID formats in the URL (for example: /api/claims/abc).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleInvalidIdFormat(MethodArgumentTypeMismatchException ex) {
        return buildErrorResponse("Invalid record ID format. The ID must be a valid number.", HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles MySQL connection or database query failures with a friendly message.
     */
    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    public ResponseEntity<Map<String, String>> handleDatabaseException(Exception ex) {
        return buildErrorResponse(
                "Database connection error: Unable to reach the MySQL database (trust_guide). Please verify that MySQL Server is running and your credentials in application.properties are correct.",
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
