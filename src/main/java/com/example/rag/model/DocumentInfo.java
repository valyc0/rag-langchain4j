package com.example.rag.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Informazioni su un documento indicizzato
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentInfo {
    private String filename;
    private DocumentStatus status;
    private int chunks;
    private Long uploadTimestamp;
    private Long readyTimestamp;
    private String errorMessage;
    
    public DocumentInfo(String filename, DocumentStatus status) {
        this.filename = filename;
        this.status = status;
        this.uploadTimestamp = System.currentTimeMillis();
    }
}
