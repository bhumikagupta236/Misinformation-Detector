package com.datastellers.detector;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Service class containing the baseline keyword-based misinformation analysis logic,
 * True/False percentage scoring, and database CRUD methods.
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
     * Ensures the news_claims table exists in the database.
     */
    private void ensureTableExists() {
        if (!tableChecked) {
            String createTableSql = """
                    CREATE TABLE IF NOT EXISTS news_claims (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        claim_text TEXT NOT NULL,
                        result VARCHAR(100) NOT NULL,
                        true_percentage INT NOT NULL DEFAULT 50,
                        false_percentage INT NOT NULL DEFAULT 50,
                        explanation TEXT NOT NULL,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """;
            jdbcTemplate.execute(createTableSql);
            tableChecked = true;
        }
    }

    /**
     * Analyzes a news claim using keyword scoring, calculates True/False percentages,
     * and saves the result in the database.
     */
    public NewsClaim analyzeAndSaveClaim(String claimText) {
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
     * Retrieves all saved analysis records from the database, ordered newest first.
     */
    public List<NewsClaim> getAllHistory() {
        ensureTableExists();
        return newsClaimRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Deletes a single history record by ID if it exists.
     */
    public boolean deleteClaimById(Long id) {
        ensureTableExists();
        if (!newsClaimRepository.existsById(id)) {
            return false;
        }
        newsClaimRepository.deleteById(id);
        return true;
    }

    /**
     * Deletes all history records from the database table.
     */
    public void clearAllHistory() {
        ensureTableExists();
        newsClaimRepository.deleteAll();
    }
}
