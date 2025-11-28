package com.example.ragclient.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentListResponse {
    private Integer total_documents;
    private Integer total_chunks;
    private Map<String, Integer> documents;
    private Map<String, Long> timestamps;
}
