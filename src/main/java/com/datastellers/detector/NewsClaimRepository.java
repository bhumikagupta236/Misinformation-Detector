package com.datastellers.detector;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Repository interface for performing database operations on the news_claims table.
 * Spring Data JPA automatically implements basic CRUD methods (save, findById, deleteById, deleteAll).
 */
@Repository
public interface NewsClaimRepository extends JpaRepository<NewsClaim, Long> {

    // Retrieves all analyzed claims ordered by newest first
    List<NewsClaim> findAllByOrderByCreatedAtDesc();
}
