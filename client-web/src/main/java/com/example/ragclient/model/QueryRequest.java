package com.example.ragclient.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Richiesta di query al RAG
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryRequest {
    private String question;
}
