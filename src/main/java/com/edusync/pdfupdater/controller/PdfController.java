package com.edusync.pdfupdater.controller;

import com.edusync.pdfupdater.service.PdfProcessorService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class PdfController {

    private final PdfProcessorService pdfProcessorService;

    public PdfController(PdfProcessorService pdfProcessorService) {
        this.pdfProcessorService = pdfProcessorService;
    }

    @org.springframework.beans.factory.annotation.Value("${spring.servlet.multipart.max-file-size:50MB}")
    private String maxFileSize;

    @GetMapping("/")
    public String landing() {
        return "landing";
    }

    @GetMapping("/app")
    public String index(org.springframework.ui.Model model) {
        String limit = maxFileSize.toUpperCase().replace("MB", "").trim();
        model.addAttribute("maxFileSizeMb", limit);
        return "index";
    }

    @GetMapping("/auth")
    public String auth() {
        return "auth";
    }

    @GetMapping("/profile")
    public String profile() {
        return "profile";
    }

    @PostMapping("/upload")
    public ResponseEntity<byte[]> handleFileUpload(@RequestParam("file") MultipartFile file,
                                                   RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .header("X-Error-Message", "File is empty.")
                    .build();
        }
        
        // Server-side validation to strictly enforce PDF uploads
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals(MediaType.APPLICATION_PDF_VALUE)) {
            log.warn("Rejected non-PDF file upload: {}", contentType);
            return ResponseEntity.status(422)
                    .header("X-Error-Message", "Only PDF files are supported.")
                    .build();
        }

        try (java.io.InputStream is = file.getInputStream()) {
            PdfProcessorService.ProcessResult result = pdfProcessorService.processPdf(is);
            byte[] updatedPdf = result.getPdfBytes();

            // Serialize updates to JSON and Base64 encode
            ObjectMapper objectMapper = new ObjectMapper();
            String updatesJson = objectMapper.writeValueAsString(result.getUpdates());
            String encodedUpdates = Base64.getEncoder().encodeToString(updatesJson.getBytes(StandardCharsets.UTF_8));

            // Sanitize filename to prevent HTTP Response Splitting and Path Traversal
            String originalName = file.getOriginalFilename();
            if (originalName == null) originalName = "document.pdf";
            String sanitizedFilename = originalName.replaceAll("[^a-zA-Z0-9\\.\\-_]", "_");
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"updated_" + sanitizedFilename + "\"")
                    .header("X-Document-Updates", encodedUpdates)
                    .header("Access-Control-Expose-Headers", "X-Document-Updates")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(updatedPdf);

        } catch (IllegalArgumentException e) {
            log.warn("Validation failed: " + e.getMessage());
            return ResponseEntity.status(422)
                    .header("X-Error-Message", e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Internal server error during PDF processing: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .header("X-Error-Message", "An unexpected error occurred on the server.")
                    .build();
        }
    }

}
