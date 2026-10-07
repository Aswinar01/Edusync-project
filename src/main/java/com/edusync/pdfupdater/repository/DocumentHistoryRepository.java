package com.edusync.pdfupdater.repository;

import com.edusync.pdfupdater.model.DocumentHistory;
import com.edusync.pdfupdater.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentHistoryRepository extends JpaRepository<DocumentHistory, Long> {
    // Only fetch metadata, excluding the heavy fileData LOB
    @org.springframework.data.jpa.repository.Query("SELECT new com.edusync.pdfupdater.model.DocumentHistory(d.id, d.user, d.fileName, d.fileType, d.fileSize, d.uploadedAt, d.docType) FROM DocumentHistory d WHERE d.user = :user ORDER BY d.uploadedAt DESC")
    List<DocumentHistory> findMetadataByUserOrderByUploadedAtDesc(User user);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM DocumentHistory d WHERE d.id = :id AND d.user.id = :userId")
    void deleteByIdAndUserId(Long id, Long userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM DocumentHistory d WHERE d.user.id = :userId")
    void deleteAllByUserId(Long userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE DocumentHistory d SET d.fileName = :fileName WHERE d.id = :id AND d.user.id = :userId")
    void renameByIdAndUserId(Long id, Long userId, String fileName);
}
