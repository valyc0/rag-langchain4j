package com.example.ragclient.service;

import com.example.ragclient.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Servizio per comunicare con le API del backend RAG usando RestClient
 */
@Service
@Slf4j
public class RagApiService {

    private final RestClient restClient;
    
    @Value("${rag.api.base-url}")
    private String baseUrl;

    public RagApiService(RestClient ragApiRestClient) {
        this.restClient = ragApiRestClient;
    }
    
    /**
     * Restituisce l'URL base del backend RAG
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Health check dell'API documenti
     */
    public HealthResponse checkDocumentsHealth() {
        try {
            return restClient.get()
                    .uri("/api/documents/health")
                    .retrieve()
                    .body(HealthResponse.class);
        } catch (Exception e) {
            log.error("Errore health check documenti: {}", e.getMessage());
            HealthResponse response = new HealthResponse();
            response.setStatus("DOWN");
            response.setService("Document Processing API - Error: " + e.getMessage());
            return response;
        }
    }

    /**
     * Health check dell'API query
     */
    public HealthResponse checkQueryHealth() {
        try {
            return restClient.get()
                    .uri("/api/query/health")
                    .retrieve()
                    .body(HealthResponse.class);
        } catch (Exception e) {
            log.error("Errore health check query: {}", e.getMessage());
            HealthResponse response = new HealthResponse();
            response.setStatus("DOWN");
            response.setService("Query API - Error: " + e.getMessage());
            return response;
        }
    }

    /**
     * Ottiene la lista dei documenti indicizzati
     */
    public DocumentListResponse getDocumentsList() {
        try {
            return restClient.get()
                    .uri("/api/documents/list")
                    .retrieve()
                    .body(DocumentListResponse.class);
        } catch (Exception e) {
            log.error("Errore recupero lista documenti: {}", e.getMessage());
            return new DocumentListResponse();
        }
    }

    /**
     * Upload di un documento
     */
    public UploadResponse uploadDocument(String filename, byte[] content) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    return filename;
                }
            });

            return restClient.post()
                    .uri("/api/documents/upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(UploadResponse.class);
        } catch (RestClientException e) {
            log.error("Errore upload documento: {}", e.getMessage());
            UploadResponse response = new UploadResponse();
            response.setMessage("❌ Errore upload: " + e.getMessage());
            return response;
        } catch (Exception e) {
            log.error("Errore upload documento: {}", e.getMessage());
            UploadResponse response = new UploadResponse();
            response.setMessage("❌ Errore upload: " + e.getMessage());
            return response;
        }
    }

    /**
     * Esegue una query RAG
     */
    public QueryResponse query(String question) {
        log.info(">>> Inizio query RAG: {}", question);
        try {
            QueryRequest request = new QueryRequest(question);
            log.debug(">>> Request body: {}", request);
            
            QueryResponse response = restClient.post()
                    .uri("/api/query")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(QueryResponse.class);
            
            log.info(">>> Risposta ricevuta - answer length: {}, sources: {}", 
                    response != null && response.getAnswer() != null ? response.getAnswer().length() : 0,
                    response != null && response.getSources() != null ? response.getSources().size() : 0);
            log.debug(">>> Response completa: {}", response);
            
            return response;
        } catch (RestClientException e) {
            log.error(">>> Errore RestClient query RAG: {}", e.getMessage(), e);
            QueryResponse response = new QueryResponse();
            response.setAnswer("❌ Errore nella query: " + e.getMessage());
            response.setQuestion(question);
            return response;
        } catch (Exception e) {
            log.error(">>> Errore generico query RAG: {}", e.getMessage(), e);
            QueryResponse response = new QueryResponse();
            response.setAnswer("❌ Errore nella query: " + e.getMessage());
            response.setQuestion(question);
            return response;
        }
    }

    /**
     * Cancella un documento
     */
    public DeleteResponse deleteDocument(String filename) {
        log.info(">>> Richiesta cancellazione documento: {}", filename);
        try {
            return restClient.delete()
                    .uri("/api/documents/{filename}", filename)
                    .retrieve()
                    .body(DeleteResponse.class);
        } catch (RestClientException e) {
            log.error(">>> Errore RestClient cancellazione documento: {}", e.getMessage(), e);
            DeleteResponse response = new DeleteResponse();
            response.setStatus("error");
            response.setMessage("❌ Errore cancellazione: " + e.getMessage());
            response.setFilename(filename);
            return response;
        } catch (Exception e) {
            log.error(">>> Errore generico cancellazione documento: {}", e.getMessage(), e);
            DeleteResponse response = new DeleteResponse();
            response.setStatus("error");
            response.setMessage("❌ Errore cancellazione: " + e.getMessage());
            response.setFilename(filename);
            return response;
        }
    }

    /**
     * Verifica se il backend è raggiungibile
     */
    public boolean isBackendAvailable() {
        try {
            HealthResponse health = checkDocumentsHealth();
            return "UP".equals(health.getStatus());
        } catch (Exception e) {
            return false;
        }
    }
}
