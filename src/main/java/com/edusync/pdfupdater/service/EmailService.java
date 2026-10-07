package com.edusync.pdfupdater.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class EmailService {

    @Value("${brevo.api.key:}")
    private String brevoApiKey;

    @Value("${spring.mail.username:onemintechdaily@gmail.com}")
    private String fromEmail;

    public void sendEmailHttp(String toEmail, String subject, String bodyText) {
        if (brevoApiKey == null || brevoApiKey.trim().isEmpty()) {
            System.err.println("BREVO_API_KEY is not set. Cannot send email.");
            return;
        }

        try {
            // Escape newlines for JSON payload
            String escapedBody = bodyText.replace("\n", "\\n").replace("\"", "\\\"");
            
            String jsonPayload = "{"
                    + "\"sender\":{\"name\":\"EduSync\",\"email\":\"" + fromEmail + "\"},"
                    + "\"to\":[{\"email\":\"" + toEmail + "\"}],"
                    + "\"subject\":\"" + subject + "\","
                    + "\"textContent\":\"" + escapedBody + "\""
                    + "}";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .header("accept", "application/json")
                    .header("api-key", brevoApiKey)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.println("OTP Email successfully sent via Brevo API.");
            } else {
                System.err.println("Brevo API Error: " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            System.err.println("Failed to send email via HTTP API: " + e.getMessage());
        }
    }
}
