package com.example.rag.service;

import com.example.rag.model.DocumentInfo;
import com.example.rag.model.DocumentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servizio per gestire lo stato dei documenti durante il processamento
 */
@Service
@Slf4j
public class DocumentStatusService {
    
    private final Map<String, DocumentInfo> documentStatuses = new ConcurrentHashMap<>();
    
    /**
     * Registra un nuovo documento in stato PROCESSING
     */
    public void registerDocument(String filename) {
        DocumentInfo info = new DocumentInfo(filename, DocumentStatus.PROCESSING);
        documentStatuses.put(filename, info);
        log.info("📝 Documento registrato in PROCESSING: {}", filename);
    }
    
    /**
     * Marca un documento come READY
     */
    public void markReady(String filename, int chunks) {
        DocumentInfo info = documentStatuses.get(filename);
        if (info != null) {
            info.setStatus(DocumentStatus.READY);
            info.setChunks(chunks);
            info.setReadyTimestamp(System.currentTimeMillis());
            log.info("✅ Documento marcato come READY: {} ({} chunks)", filename, chunks);
        }
    }
    
    /**
     * Marca un documento come ERROR
     */
    public void markError(String filename, String errorMessage) {
        DocumentInfo info = documentStatuses.get(filename);
        if (info != null) {
            info.setStatus(DocumentStatus.ERROR);
            info.setErrorMessage(errorMessage);
            log.error("❌ Documento marcato come ERROR: {} - {}", filename, errorMessage);
        }
    }
    
    /**
     * Ottiene lo stato di un documento
     */
    public DocumentInfo getDocumentStatus(String filename) {
        return documentStatuses.get(filename);
    }
    
    /**
     * Ottiene tutti gli stati dei documenti
     */
    public Map<String, DocumentInfo> getAllStatuses() {
        return new ConcurrentHashMap<>(documentStatuses);
    }
    
    /**
     * Rimuove un documento dallo stato
     */
    public void removeDocument(String filename) {
        documentStatuses.remove(filename);
        log.info("🗑️ Documento rimosso dallo stato: {}", filename);
    }
}
