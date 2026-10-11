-- ============================================================
-- Trust Guide – Misinformation Detector
-- MySQL Database Setup Script
-- ============================================================

-- 1. Create the database if it does not already exist
CREATE DATABASE IF NOT EXISTS trust_guide
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 2. Select the database
USE trust_guide;

-- 3. Create the news_claims table
CREATE TABLE IF NOT EXISTS news_claims (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    claim_text TEXT NOT NULL,
    result VARCHAR(100) NOT NULL,
    true_percentage INT NOT NULL DEFAULT 50,
    false_percentage INT NOT NULL DEFAULT 50,
    explanation TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
