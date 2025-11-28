package com.example.ragclient.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Risposta dallo stato di un documento
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentStatusResponse {
    private String filename;
    private String status; // PROCESSING, READY, ERROR
    private int chunks;
    private Long uploadTimestamp;
    private Long readyTimestamp;
    private String errorMessage;
}
