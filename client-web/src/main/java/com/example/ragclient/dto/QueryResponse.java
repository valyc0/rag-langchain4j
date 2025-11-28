package com.example.ragclient.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryResponse {
    private String answer;
    private List<Source> sources;
    private String question;
    private Integer chunks_used;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Source {
        private String text;
        private Double score;
        private String filename;
    }
}
