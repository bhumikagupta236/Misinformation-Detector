package com.datastellers.detector;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service class containing the baseline keyword-based misinformation analysis logic,
 * True/False percentage scoring, analytics computation, and database CRUD methods.
 */
@Service
public class NewsClaimService {

    private final NewsClaimRepository newsClaimRepository;
    private final JdbcTemplate jdbcTemplate;
    private boolean tableChecked = false;

    // Documented baseline list of sensational or clickbait expressions
    private static final List<String> SUSPICIOUS_EXPRESSIONS = Arrays.asList(
            "shocking truth",
            "secret revealed",
            "100% guaranteed",
            "share immediately",
            "they don't want you to know",
            "miracle cure",
            "doctors hate",
            "forwarded many times",
            "urgent warning",
            "banned video",
            "wake up people",
            "conspiracy exposed",
            "unbelievable secret",
            "delete before they ban"
    );

    // Baseline list of neutral / institutional reporting terms
    private static final List<String> FACTUAL_CONTEXT_TERMS = Arrays.asList(
            "according to",
            "official",
            "ministry",
            "university",
            "research",
            "published",
            "announced",
            "report",
            "department",
            "study",
            "committee",
            "data"
    );

    public NewsClaimService(NewsClaimRepository newsClaimRepository, JdbcTemplate jdbcTemplate) {
        this.newsClaimRepository = newsClaimRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Ensures the database tables exist and are properly migrated.
     */
    private void ensureTableExists() {
        if (!tableChecked) {
            try {
                // 1. Create users table
                String createUsersSql = """
                        CREATE TABLE IF NOT EXISTS users (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            full_name VARCHAR(150) NOT NULL,
                            email VARCHAR(150) NOT NULL UNIQUE,
                            password_hash VARCHAR(255) NOT NULL,
                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                        )
                        """;
                jdbcTemplate.execute(createUsersSql);

                // 2. Create news_claims table
                String createClaimsSql = """
                        CREATE TABLE IF NOT EXISTS news_claims (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            user_id BIGINT NULL,
                            claim_text TEXT NOT NULL,
                            result VARCHAR(100) NOT NULL,
                            true_percentage INT NOT NULL DEFAULT 50,
                            false_percentage INT NOT NULL DEFAULT 50,
                            explanation TEXT NOT NULL,
                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                        )
                        """;
                jdbcTemplate.execute(createClaimsSql);

                // 3. Add user_id column if table was previously created without it
                try {
                    jdbcTemplate.execute("ALTER TABLE news_claims ADD COLUMN IF NOT EXISTS user_id BIGINT NULL");
                } catch (Exception ignored) {
                    // Column already exists or syntax variation
                }

                tableChecked = true;
            } catch (Exception e) {
                // Allow application to proceed; JPA will also validate tables
                tableChecked = true;
            }
        }
    }

    /**
     * Overload for analyzing and saving without a logged-in user.
     */
    public NewsClaim analyzeAndSaveClaim(String claimText) {
        return analyzeAndSaveClaim(claimText, null);
    }

    /**
     * Analyzes a news claim using keyword scoring, calculates True/False percentages,
     * and saves the record linked to the user in the database.
     */
    public NewsClaim analyzeAndSaveClaim(String claimText, Long userId) {
        String cleanedClaim = claimText.trim();
        String lowerCaseClaim = cleanedClaim.toLowerCase();

        List<String> matchedKeywords = new ArrayList<>();
        int score = 0;

        // 1. Check for suspicious or sensational phrases (case-insensitive)
        for (String expression : SUSPICIOUS_EXPRESSIONS) {
            if (lowerCaseClaim.contains(expression)) {
                matchedKeywords.add("\"" + expression + "\"");
                score++;
            }
        }

        // 2. Check for excessive exclamation marks often used in sensational forwards
        boolean hasExcessiveExclamations = cleanedClaim.contains("!!!");
        if (hasExcessiveExclamations) {
            score++;
        }

        // 3. Check for neutral or institutional reporting context terms
        int factualCount = 0;
        for (String term : FACTUAL_CONTEXT_TERMS) {
            if (lowerCaseClaim.contains(term)) {
                factualCount++;
            }
        }

        // 4. Calculate estimated True (Reliable) % and False (Misleading) %
        int falsePercentage;
        int truePercentage;
        String result;
        String explanation;

        String methodologyNote = " Note: This is a basic keyword-based heuristic demonstration and not a trained machine-learning fact-checking model.";

        if (score >= 2) {
            falsePercentage = Math.min(92, Math.max(68, 55 + (score * 12) - (factualCount * 4)));
            truePercentage = 100 - falsePercentage;
            result = "Potentially Misleading";
            explanation = "Estimated " + falsePercentage + "% Misleading / False Risk (" + truePercentage + "% Reliability Score). "
                    + "Detected multiple sensational indicators (Score: " + score + ") including "
                    + formatIndicators(matchedKeywords, hasExcessiveExclamations)
                    + ". Claims using emotional or clickbait phrasing frequently spread unverified rumors, though this check does not definitively prove the story is false."
                    + methodologyNote;
        } else if (score == 1) {
            falsePercentage = Math.min(75, Math.max(58, 65 - (factualCount * 5)));
            truePercentage = 100 - falsePercentage;
            result = "Potentially Misleading";
            explanation = "Estimated " + falsePercentage + "% Misleading / False Risk (" + truePercentage + "% Reliability Score). "
                    + "Detected 1 sensational indicator (Score: 1): "
                    + formatIndicators(matchedKeywords, hasExcessiveExclamations)
                    + ". Containing a suspicious expression does not mean a claim is definitely fake, but it is a common sign of clickbait that should be verified carefully."
                    + methodologyNote;
        } else {
            truePercentage = Math.min(88, 68 + (factualCount * 6));
            falsePercentage = 100 - truePercentage;
            result = "Needs Verification";
            explanation = "Estimated " + truePercentage + "% Reliability / True Score (" + falsePercentage + "% Unverified / False Risk). "
                    + "No sensational or clickbait expressions from our baseline list were found (Score: 0). "
                    + "However, the absence of suspicious keywords is not proof that a claim is true—please verify the facts with reliable news organizations."
                    + methodologyNote;
        }

        // 5. Create the entity and save it in the database
        ensureTableExists();
        NewsClaim newsClaim = new NewsClaim(
                userId,
                cleanedClaim,
                result,
                truePercentage,
                falsePercentage,
                explanation,
                LocalDateTime.now()
        );

        return newsClaimRepository.save(newsClaim);
    }

    /**
     * Helper method to build a readable description of matched indicators.
     */
    private String formatIndicators(List<String> matchedKeywords, boolean hasExcessiveExclamations) {
        List<String> details = new ArrayList<>(matchedKeywords);
        if (hasExcessiveExclamations) {
            details.add("excessive exclamation marks (!!!)");
        }
        return String.join(", ", details);
    }

    /**
     * Retrieves a single claim by its primary key ID.
     */
    public Optional<NewsClaim> getClaimById(Long id) {
        ensureTableExists();
        return newsClaimRepository.findById(id);
    }

    /**
     * Retrieves all saved analysis records from the database, ordered newest first.
     */
    public List<NewsClaim> getAllHistory() {
        return getAllHistory(null);
    }

    /**
     * Retrieves history records, filtered by user ID if provided.
     */
    public List<NewsClaim> getAllHistory(Long userId) {
        ensureTableExists();
        if (userId != null) {
            List<NewsClaim> userClaims = newsClaimRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
            if (!userClaims.isEmpty()) {
                return userClaims;
            }
        }
        // If no user claims found or public guest, return general history
        return newsClaimRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Deletes a single history record by ID.
     */
    public boolean deleteClaimById(Long id) {
        return deleteClaimById(id, null);
    }

    /**
     * Deletes a single history record by ID, verifying user ownership if userId is supplied.
     */
    public boolean deleteClaimById(Long id, Long userId) {
        ensureTableExists();
        Optional<NewsClaim> optional = newsClaimRepository.findById(id);
        if (optional.isEmpty()) {
            return false;
        }

        NewsClaim claim = optional.get();
        // If a specific user is logged in and the claim belongs to a different user, prevent unauthorized delete
        if (userId != null && claim.getUserId() != null && !userId.equals(claim.getUserId())) {
            return false;
        }

        newsClaimRepository.deleteById(id);
        return true;
    }

    /**
     * Deletes all history records from the database table.
     */
    public void clearAllHistory() {
        clearAllHistory(null);
    }

    /**
     * Deletes history records for a specific user (or all if userId is null).
     */
    @Transactional
    public void clearAllHistory(Long userId) {
        ensureTableExists();
        if (userId != null) {
            newsClaimRepository.deleteAllByUserId(userId);
        } else {
            newsClaimRepository.deleteAll();
        }
    }

    /**
     * Calculates analytics statistics from actual database records.
     */
    public Map<String, Object> getAnalytics(Long userId) {
        ensureTableExists();
        List<NewsClaim> claims = (userId != null)
                ? newsClaimRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                : newsClaimRepository.findAllByOrderByCreatedAtDesc();

        // If user has no personal claims yet, fallback to all claims for a rich visualization
        if (claims.isEmpty() && userId != null) {
            claims = newsClaimRepository.findAllByOrderByCreatedAtDesc();
        }

        long totalClaims = claims.size();
        long misleadingCount = 0;
        long reliableCount = 0;
        double sumTrue = 0;
        double sumFalse = 0;

        for (NewsClaim claim : claims) {
            if ("Potentially Misleading".equalsIgnoreCase(claim.getResult())) {
                misleadingCount++;
            } else {
                reliableCount++;
            }
            sumTrue += claim.getTruePercentage();
            sumFalse += claim.getFalsePercentage();
        }

        double avgTrue = totalClaims > 0 ? Math.round((sumTrue / totalClaims) * 10.0) / 10.0 : 0.0;
        double avgFalse = totalClaims > 0 ? Math.round((sumFalse / totalClaims) * 10.0) / 10.0 : 0.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalClaims", totalClaims);
        stats.put("misleadingCount", misleadingCount);
        stats.put("reliableCount", reliableCount);
        stats.put("averageTruePercentage", avgTrue);
        stats.put("averageFalsePercentage", avgFalse);

        // Chart data structures for Chart.js
        List<String> labels = Arrays.asList("Potentially Misleading", "Needs Verification (Reliable)");
        List<Long> counts = Arrays.asList(misleadingCount, reliableCount);
        stats.put("chartLabels", labels);
        stats.put("chartCounts", counts);

        return stats;
    }
}
