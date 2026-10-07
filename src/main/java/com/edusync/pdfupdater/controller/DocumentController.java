package com.edusync.pdfupdater.controller;

import com.edusync.pdfupdater.model.DocumentHistory;
import com.edusync.pdfupdater.model.User;
import com.edusync.pdfupdater.repository.DocumentHistoryRepository;
import com.edusync.pdfupdater.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
@Transactional
public class DocumentController {

    @Autowired
    private DocumentHistoryRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    // Get all documents for the logged in user
    @GetMapping
    public ResponseEntity<?> getDocuments(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        User user = userRepository.findById(userId).orElseThrow();
        // Use optimized query that excludes fileData to improve speed and save memory
        List<DocumentHistory> docs = documentRepository.findMetadataByUserOrderByUploadedAtDesc(user);
        
        List<Map<String, Object>> response = docs.stream().map(d -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", d.getId());
            map.put("name", d.getFileName());
            map.put("type", d.getDocType() != null ? d.getDocType() : "Original");
            map.put("size", d.getFileSize());
            map.put("date", d.getUploadedAt().toString());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // Save a new document
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> saveDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(value = "type", defaultValue = "Original") String type,
            HttpSession session) {
        
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        
        // Strict Security Validation: Only allow PDFs to prevent XSS/Malware uploads
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals(MediaType.APPLICATION_PDF_VALUE)) {
            return ResponseEntity.status(422).body(Map.of("error", "Only PDF files are supported."));
        }
        
        User user = userRepository.findById(userId).orElseThrow();

        try {
            DocumentHistory doc = new DocumentHistory();
            doc.setUser(user);
            // Sanitize filename
            String originalName = name;
            if (originalName == null || originalName.trim().isEmpty()) originalName = file.getOriginalFilename();
            if (originalName == null) originalName = "document.pdf";
            String sanitizedFilename = originalName.replaceAll("[^a-zA-Z0-9\\.\\-_ ]", "_");
            
            doc.setFileName(sanitizedFilename);
            doc.setFileType(MediaType.APPLICATION_PDF_VALUE);
            doc.setFileSize(file.getSize());
            doc.setFileData(file.getBytes());
            doc.setDocType(type);
            documentRepository.save(doc);

            return ResponseEntity.ok(Map.of("message", "Document saved", "id", doc.getId()));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to process file"));
        }
    }

    // Download a document
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long id, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Optional<DocumentHistory> docOpt = documentRepository.findById(id);
        if (docOpt.isEmpty() || !docOpt.get().getUser().getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        DocumentHistory doc = docOpt.get();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getFileName() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(doc.getFileType()))
                .body(doc.getFileData());
    }

    // Rename a document
    @PutMapping("/{id}/rename")
    public ResponseEntity<?> renameDocument(@PathVariable Long id, @RequestBody Map<String, String> payload, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        String newName = payload.get("name");
        if (newName == null || newName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Name cannot be empty"));
        }

        documentRepository.renameByIdAndUserId(id, userId, newName.trim());
        return ResponseEntity.ok(Map.of("message", "Renamed"));
    }

    // Delete a document
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDocument(@PathVariable Long id, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        // Use optimized query that skips loading fileData into memory
        documentRepository.deleteByIdAndUserId(id, userId);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }
    
    // Delete all documents
    @DeleteMapping("/all")
    public ResponseEntity<?> deleteAllDocuments(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        
        // Use optimized query that skips loading all fileData into memory
        documentRepository.deleteAllByUserId(userId);
        return ResponseEntity.ok(Map.of("message", "All deleted"));
    }
}
