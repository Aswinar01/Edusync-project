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
    private EmailService emailService;

    @Value("${spring.mail.username}")
    private String fromEmail;

    private final Random random = new Random();

    public String[] generateAndSendOtp(String email) {
        // Generate a 6-digit OTP
        String otp = String.format("%06d", random.nextInt(999999));
        
        // Hash the OTP securely for verification (salt:hash)
        // We use fast SHA-256 hashing here (legacyHashPassword) instead of BCrypt
        // because BCrypt is too slow on free-tier cloud VMs and blocks the UI for seconds.
        // Use ThreadLocalRandom instead of UUID to prevent /dev/random blocking on tiny cloud VMs
        String salt = Long.toHexString(java.util.concurrent.ThreadLocalRandom.current().nextLong());
        String otpHash = salt + ":" + AuthUtils.legacyHashPassword(otp + salt);
        
            new Thread(() -> {
                try {
                    emailService.sendEmailHttp(
                        email, 
                        "Your EduSync Verification Code", 
                        "Welcome to EduSync!\n\nYour 6-digit verification code is: " + otp + "\n\nPlease enter this code to complete your registration.\n\nThanks,\nThe EduSync Team"
                    );
                } catch (Exception e) {
                    System.err.println("Failed to trigger email API: " + e.getMessage());
                }
            }).start();

        // Return only the hash to the controller
        return new String[]{otp, otpHash};
    }
    
    public boolean verifyOtp(String enteredOtp, String expectedHash) {
        if (enteredOtp == null || expectedHash == null || !expectedHash.contains(":")) return false;
        String[] parts = expectedHash.split(":");
        if (parts.length != 2) return false;
        String salt = parts[0];
        String hash = parts[1];
        
        // Check using the fast SHA-256 hash (legacyHashPassword)
        return AuthUtils.legacyHashPassword(enteredOtp + salt).equals(hash);
    }
}
