-- ============================================================
-- Trust Guide – Misinformation Detector
-- MySQL / SQL Database Setup Script
-- ============================================================

-- 1. Create the database if it does not already exist
CREATE DATABASE IF NOT EXISTS trust_guide
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 2. Select the database
USE trust_guide;

-- 3. Create the users table for authentication
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. Create the news_claims table
CREATE TABLE IF NOT EXISTS news_claims (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    claim_text TEXT NOT NULL,
    result VARCHAR(100) NOT NULL,
    true_percentage INT NOT NULL DEFAULT 50,
    false_percentage INT NOT NULL DEFAULT 50,
    explanation TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_news_claims_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
