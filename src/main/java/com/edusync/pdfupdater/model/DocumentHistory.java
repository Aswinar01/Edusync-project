package com.edusync.pdfupdater.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentHistory {

    public DocumentHistory(Long id, User user, String fileName, String fileType, Long fileSize, LocalDateTime uploadedAt, String docType) {
        this.id = id;
        this.user = user;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.uploadedAt = uploadedAt;
        this.docType = docType;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The user who owns this document (nullable for guests before migration)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String fileType; // e.g. "application/pdf"

    @Column(nullable = false)
    private Long fileSize; // in bytes

    @Column(nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();
    
    @Column(name = "doc_type", nullable = true)
    private String docType = "Original";

    // Large object to store the binary file content directly in PostgreSQL (or we can use S3/Supabase Storage later)
    // For MVP, we can store it as a LOB or byte array
    @Lob
    @Column(nullable = false)
    private byte[] fileData;
}
