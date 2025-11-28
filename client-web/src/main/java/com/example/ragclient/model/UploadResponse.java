package com.example.ragclient.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Risposta dall'upload di un documento
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UploadResponse {
    private String message;
    private UploadData data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UploadData {
        private String filename;
        
        @JsonProperty("size_bytes")
        private long sizeBytes;
        
        @JsonProperty("text_length")
        private int textLength;
        
        @JsonProperty("chunks_created")
        private int chunksCreated;
        
        @JsonProperty("embedding_dimension")
        private int embeddingDimension;
        
        private String status;
    }
}
