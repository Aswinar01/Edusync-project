package com.edusync.pdfupdater.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Async
    public void sendEmailAsync(SimpleMailMessage message) {
        try {
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send email asynchronously: " + e.getMessage());
        }
    }
}
