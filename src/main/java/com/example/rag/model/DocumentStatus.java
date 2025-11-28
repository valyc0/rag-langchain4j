package com.example.rag.model;

/**
 * Stati possibili per un documento durante il processamento
 */
public enum DocumentStatus {
    /**
     * Upload ricevuto, documento in coda per il processamento
     */
    PROCESSING,
    
    /**
     * Documento processato e pronto per le query
     */
    READY,
    
    /**
     * Errore durante il processamento
     */
    ERROR
}
