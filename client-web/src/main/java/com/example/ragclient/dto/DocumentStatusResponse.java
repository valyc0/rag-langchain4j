package com.example.ragclient.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentStatusResponse {
    private String filename;
    private String status;
    private Integer chunks;
    private Long uploadTimestamp;
    private Long readyTimestamp;
    private String errorMessage;
}
