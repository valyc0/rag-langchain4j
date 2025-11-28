package com.example.ragclient.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Risposta con tutti gli stati dei documenti
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AllStatusesResponse {
    private Map<String, DocumentStatusInfo> statuses;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DocumentStatusInfo {
        private String status;
        private int chunks;
        private Long uploadTimestamp;
        private Long readyTimestamp;
        private String errorMessage;
    }
}
