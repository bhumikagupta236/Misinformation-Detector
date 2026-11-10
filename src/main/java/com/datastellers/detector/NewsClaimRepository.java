package com.datastellers.detector;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Repository interface for database operations on the news_claims table.
 */
@Repository
public interface NewsClaimRepository extends JpaRepository<NewsClaim, Long> {

    // Retrieves all claims ordered newest first
    List<NewsClaim> findAllByOrderByCreatedAtDesc();

    // Retrieves claims created by a specific user ordered newest first
    List<NewsClaim> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    // Count claims for analytics
    long countByUserId(Long userId);

    long countByResult(String result);

    long countByUserIdAndResult(Long userId, String result);

    void deleteAllByUserId(Long userId);
}
