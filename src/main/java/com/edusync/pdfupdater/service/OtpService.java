package com.edusync.pdfupdater.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import com.edusync.pdfupdater.util.AuthUtils;
import java.util.Random;

@Service
public class OtpService {
    
    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    private final Random random = new Random();

    public String[] generateAndSendOtp(String email) {
        // Generate a 6-digit OTP
        String otp = String.format("%06d", random.nextInt(999999));
        
        // Hash the OTP securely for verification (salt:hash)
        String salt = java.util.UUID.randomUUID().toString();
        String otpHash = salt + ":" + AuthUtils.hashPassword(otp + salt);
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("Your EduSync Verification Code");
            message.setText("Welcome to EduSync!\n\nYour 6-digit verification code is: " + otp + "\n\nPlease enter this code to complete your registration.\n\nThanks,\nThe EduSync Team");
            
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    mailSender.send(message);
                } catch (Exception e) {
                    System.err.println("Failed to send OTP email asynchronously: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to prepare OTP email.");
        }
        
        // Return only the hash to the controller
        return new String[]{otp, otpHash};
    }
    
    public boolean verifyOtp(String enteredOtp, String expectedHash) {
        if (enteredOtp == null || expectedHash == null || !expectedHash.contains(":")) return false;
        String[] parts = expectedHash.split(":");
        if (parts.length != 2) return false;
        String salt = parts[0];
        String hash = parts[1];
        
        // For OTP, we still use the old raw hashing internally since we explicitly added a salt
        // If AuthUtils.hashPassword was upgraded to BCrypt, it automatically handles its own salt.
        // Wait, if AuthUtils.hashPassword is now BCrypt, we don't need a manual salt! BCrypt does it for us.
        // But since we want to be safe, we can just use BCrypt's matching on (enteredOtp + salt) or just use BCrypt natively!
        return AuthUtils.checkPassword(enteredOtp + salt, hash);
    }
}
