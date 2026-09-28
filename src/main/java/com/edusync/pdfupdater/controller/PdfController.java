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

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class PdfController {

    private final PdfProcessorService pdfProcessorService;

    public PdfController(PdfProcessorService pdfProcessorService) {
        this.pdfProcessorService = pdfProcessorService;
    }

    @GetMapping("/")
    public String index() {
        return "index";
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

        try {
            byte[] updatedPdf = pdfProcessorService.processPdf(file.getInputStream());

            // Sanitize filename to prevent HTTP Response Splitting and Path Traversal
            String originalName = file.getOriginalFilename();
            if (originalName == null) originalName = "document.pdf";
            String sanitizedFilename = originalName.replaceAll("[^a-zA-Z0-9\\.\\-_]", "_");
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"updated_" + sanitizedFilename + "\"")
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
