package com.datastellers.detector;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for hashing and verifying passwords using SHA-256 with salt.
 * Ensures that plain-text passwords are never stored in the database.
 */
public class PasswordUtil {

    // Salt to protect hashed passwords against precomputed dictionary/rainbow table attacks
    private static final String SALT = "TrustGuide_Secure_Salt_2026";

    /**
     * Hashes a plain password using SHA-256 and returns a 64-character hexadecimal string.
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String saltedInput = SALT + plainPassword;
            byte[] hashBytes = digest.digest(saltedInput.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available in environment", e);
        }
    }

    /**
     * Checks whether a plain password matches the stored hashed password.
     */
    public static boolean checkPassword(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null) {
            return false;
        }
        String computedHash = hashPassword(plainPassword);
        return computedHash.equals(storedHash);
    }
}
