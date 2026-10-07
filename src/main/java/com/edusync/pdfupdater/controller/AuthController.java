package com.edusync.pdfupdater.controller;

import com.edusync.pdfupdater.model.User;
import com.edusync.pdfupdater.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.Optional;
import com.edusync.pdfupdater.util.AuthUtils;
import com.edusync.pdfupdater.service.OtpService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private OtpService otpService;

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String type = payload.get("type"); // can be 'register' or 'reset'
        
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required."));
        }
        
        if ("register".equals(type) && userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is already registered."));
        }
        
        String[] result = otpService.generateAndSendOtp(email);
        return ResponseEntity.ok(Map.of(
            "message", "OTP sent successfully",
            "otpHash", result[1] // Send only the hash to the client
        ));
    }
    
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> payload) {
        String otp = payload.get("otp");
        String otpHash = payload.get("otpHash");
        if (otpService.verifyOtp(otp, otpHash)) {
            return ResponseEntity.ok(Map.of("message", "OTP verified successfully"));
        }
        return ResponseEntity.status(400).body(Map.of("error", "Invalid or expired OTP"));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> payload, HttpSession session) {
        String email = payload.get("email");
        String password = payload.get("password");
        String fullName = payload.get("fullName");

        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is already registered."));
        }

        User user = new User();
        user.setEmail(email);
        user.setFullName(fullName);
        if (payload.containsKey("profilePhoto")) {
            user.setProfilePhoto(payload.get("profilePhoto"));
        }
        // In a real app we'd BCrypt hash this, but keeping it simple for the MVP prototype
        user.setPasswordHash(AuthUtils.hashPassword(password)); 

        userRepository.save(user);
        
        session.setAttribute("userId", user.getId());
        return ResponseEntity.ok(Map.of("message", "Registered successfully", "userId", user.getId(), "fullName", user.getFullName()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> payload, HttpSession session) {
        String email = payload.get("email");
        String password = payload.get("password");

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Couldn't find your account."));
        }
        
        if (!userOpt.get().getPasswordHash().equals(AuthUtils.hashPassword(password))) {
            return ResponseEntity.status(401).body(Map.of("error", "Wrong password. Try again."));
        }

        User user = userOpt.get();
        session.setAttribute("userId", user.getId());
        return ResponseEntity.ok(Map.of("message", "Logged in successfully", "userId", user.getId(), "fullName", user.getFullName()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "User not found"));
        }
        User user = userOpt.get();
        return ResponseEntity.ok(Map.of("userId", user.getId(), "email", user.getEmail(), "fullName", user.getFullName(), "profilePhoto", user.getProfilePhoto() != null ? user.getProfilePhoto() : ""));
    }
    @PostMapping("/update")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> payload, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "User not found"));
        }
        
        User user = userOpt.get();
        if (payload.containsKey("email") && !payload.get("email").trim().isEmpty()) {
            user.setEmail(payload.get("email").trim());
        }
        if (payload.containsKey("fullName") && !payload.get("fullName").trim().isEmpty()) {
            user.setFullName(payload.get("fullName").trim());
        }
        if (payload.containsKey("password") && !payload.get("password").trim().isEmpty()) {
            user.setPasswordHash(AuthUtils.hashPassword(payload.get("password").trim()));
        }
        if (payload.containsKey("profilePhoto")) {
            user.setProfilePhoto(payload.get("profilePhoto"));
        }
        
        try {
            userRepository.save(user);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return ResponseEntity.status(400).body(Map.of("error", "Email is already taken by another account."));
        }
        return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));
    }
}
