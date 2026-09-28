package com.edusync.pdfupdater.model;

import lombok.Data;
import java.util.List;

@Data
public class UpdateResponse {
    private List<SentenceUpdate> updates;

    @Data
    public static class SentenceUpdate {
        private String originalSentence;
        private String updatedSentence;
        private String source;
    }
}
