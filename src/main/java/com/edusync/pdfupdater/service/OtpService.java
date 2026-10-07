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
        
        // Hash the OTP securely for verification
        String otpHash = AuthUtils.hashPassword(otp);
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("Your EduSync Verification Code");
            message.setText("Welcome to EduSync!\n\nYour 6-digit verification code is: " + otp + "\n\nPlease enter this code to complete your registration.\n\nThanks,\nThe EduSync Team");
            
            mailSender.send(message);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to send OTP email.");
        }
        
        // Return only the hash to the controller
        return new String[]{otp, otpHash};
    }
    
    public boolean verifyOtp(String enteredOtp, String expectedHash) {
        if (enteredOtp == null || expectedHash == null) return false;
        return AuthUtils.hashPassword(enteredOtp).equals(expectedHash);
    }
}
