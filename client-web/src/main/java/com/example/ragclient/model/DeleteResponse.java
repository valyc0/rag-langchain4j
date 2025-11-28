package com.example.ragclient.model;

import lombok.Data;

/**
 * Risposta dell'API di cancellazione documento
 */
@Data
public class DeleteResponse {
    private String status;
    private String message;
    private String filename;
    private Integer chunksDeleted;
}
