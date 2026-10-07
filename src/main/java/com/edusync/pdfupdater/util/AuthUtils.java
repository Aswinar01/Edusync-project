package com.edusync.pdfupdater.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class AuthUtils {
    
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public static String hashPassword(String password) {
        return encoder.encode(password);
    }
    
    public static boolean checkPassword(String rawPassword, String encodedPassword) {
        if (encodedPassword == null) return false;
        
        // Graceful upgrade: Check if the password hash is legacy (SHA-256 Base64 usually doesn't start with BCrypt identifiers)
        if (!encodedPassword.startsWith("$2a$") && !encodedPassword.startsWith("$2b$") && !encodedPassword.startsWith("$2y$")) {
            String legacyHash = legacyHashPassword(rawPassword);
            return legacyHash.equals(encodedPassword);
        }
        
        // For new BCrypt hashes
        return encoder.matches(rawPassword, encodedPassword);
    }

    public static boolean needsUpgrade(String encodedPassword) {
        if (encodedPassword == null) return false;
        return !encodedPassword.startsWith("$2a$") && !encodedPassword.startsWith("$2b$") && !encodedPassword.startsWith("$2y$");
    }

    public static String legacyHashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to hash password", e);
        }
    }
}
