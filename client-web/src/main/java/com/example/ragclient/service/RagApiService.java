package com.example.ragclient.service;

import com.example.ragclient.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagApiService {

    private final WebClient webClient;

    // ============ DOCUMENT ENDPOINTS ============

    /**
     * Upload a document to the RAG system
     */
    public UploadResponse uploadDocument(String filename, byte[] content) {
        log.info("📤 Uploading document: {}", filename);

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });

        return webClient.post()
                .uri("/api/documents/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(UploadResponse.class)
                .block();
    }

    /**
     * Get list of all indexed documents
     */
    public DocumentListResponse getDocumentList() {
        log.info("📋 Fetching document list");

        return webClient.get()
                .uri("/api/documents/list")
                .retrieve()
                .bodyToMono(DocumentListResponse.class)
                .block();
    }

    /**
     * Get status of a specific document
     */
    public DocumentStatusResponse getDocumentStatus(String filename) {
        log.info("🔍 Fetching status for: {}", filename);

        return webClient.get()
                .uri("/api/documents/status/{filename}", filename)
                .retrieve()
                .bodyToMono(DocumentStatusResponse.class)
                .block();
    }

    /**
     * Get status of all documents
     */
    public Map<String, Object> getAllDocumentStatuses() {
        log.info("📊 Fetching all document statuses");

        return webClient.get()
                .uri("/api/documents/statuses")
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    /**
     * Delete a document
     */
    public Map<String, Object> deleteDocument(String filename) {
        log.info("🗑️ Deleting document: {}", filename);

        return webClient.delete()
                .uri("/api/documents/{filename}", filename)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    /**
     * Health check for document API
     */
    public HealthResponse getDocumentHealth() {
        return webClient.get()
                .uri("/api/documents/health")
                .retrieve()
                .bodyToMono(HealthResponse.class)
                .block();
    }

    // ============ QUERY ENDPOINTS ============

    /**
     * Query the RAG system (GET method)
     */
    public QueryResponse query(String question) {
        log.info("❓ Querying: {}", question);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/query")
                        .queryParam("question", question)
                        .build())
                .retrieve()
                .bodyToMono(QueryResponse.class)
                .block();
    }

    /**
     * Query the RAG system (POST method)
     */
    public QueryResponse queryPost(String question) {
        log.info("❓ Querying (POST): {}", question);

        Map<String, String> request = new HashMap<>();
        request.put("question", question);

        return webClient.post()
                .uri("/api/query")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(QueryResponse.class)
                .block();
    }

    /**
     * Health check for query API
     */
    public HealthResponse getQueryHealth() {
        return webClient.get()
                .uri("/api/query/health")
                .retrieve()
                .bodyToMono(HealthResponse.class)
                .block();
    }

    /**
     * Check overall system health
     */
    public boolean isSystemHealthy() {
        try {
            HealthResponse docHealth = getDocumentHealth();
            HealthResponse queryHealth = getQueryHealth();
            return "UP".equals(docHealth.getStatus()) && "UP".equals(queryHealth.getStatus());
        } catch (Exception e) {
            log.error("❌ Health check failed", e);
            return false;
        }
    }
}
