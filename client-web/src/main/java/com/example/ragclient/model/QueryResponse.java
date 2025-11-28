package com.example.ragclient.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Risposta dalla query RAG
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueryResponse {
    private String answer;
    private List<Source> sources;
    private String question;
    
    @JsonProperty("chunks_used")
    private int chunksUsed;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Source {
        private String text;
        private double score;
        private String filename;
    }
}
