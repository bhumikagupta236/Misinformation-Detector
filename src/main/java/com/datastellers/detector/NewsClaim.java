package com.datastellers.detector;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Entity class representing a single news claim record stored in the database.
 * Maps to the "news_claims" table in the "trust_guide" database.
 */
@Entity
@Table(name = "news_claims")
public class NewsClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_text", columnDefinition = "TEXT", nullable = false)
    private String claimText;

    @Column(name = "result", length = 100, nullable = false)
    private String result;

    @Column(name = "true_percentage", nullable = false)
    private int truePercentage;

    @Column(name = "false_percentage", nullable = false)
    private int falsePercentage;

    @Column(name = "explanation", columnDefinition = "TEXT", nullable = false)
    private String explanation;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Default constructor required by JPA
    public NewsClaim() {
    }

    // Parameterized constructor for creating a new claim record
    public NewsClaim(
            String claimText,
            String result,
            int truePercentage,
            int falsePercentage,
            String explanation,
            LocalDateTime createdAt
    ) {
        this.claimText = claimText;
        this.result = result;
        this.truePercentage = truePercentage;
        this.falsePercentage = falsePercentage;
        this.explanation = explanation;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClaimText() {
        return claimText;
    }

    public void setClaimText(String claimText) {
        this.claimText = claimText;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public int getTruePercentage() {
        return truePercentage;
    }

    public void setTruePercentage(int truePercentage) {
        this.truePercentage = truePercentage;
    }

    public int getFalsePercentage() {
        return falsePercentage;
    }

    public void setFalsePercentage(int falsePercentage) {
        this.falsePercentage = falsePercentage;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
