package com.example.rag.controller;

import com.example.rag.service.DocumentProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * REST Controller per l'upload e l'indicizzazione dei documenti
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentProcessingService documentProcessingService;

    /**
     * Upload e indicizza un documento
     * 
     * POST /api/documents/upload
     * Content-Type: multipart/form-data
     * 
     * @param file Il file da caricare (PDF, Word, Excel, ecc.)
     * @return Statistiche sul documento processato
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file) {
        
        try {
            log.info("📤 Ricevuto file: {} ({} bytes)", 
                    file.getOriginalFilename(), file.getSize());
            
            // Validazione
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "File vuoto"));
            }
            
            if (!documentProcessingService.isSupportedFile(file.getOriginalFilename())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Tipo di file non supportato. " +
                                "Usa: PDF, DOC, DOCX, XLS, XLSX, PPT, PPTX, TXT, HTML"));
            }
            
            // Processa il documento
            Map<String, Object> result = documentProcessingService.processDocument(file);
            
            return ResponseEntity.ok(Map.of(
                "message", "✅ Documento caricato e indicizzato con successo!",
                "data", result
            ));
            
        } catch (Exception e) {
            log.error("❌ Errore durante l'upload del documento", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Errore durante il processamento del documento",
                        "details", e.getMessage()
                    ));
        }
    }

    /**
     * Lista tutti i documenti indicizzati
     * 
     * GET /api/documents/list
     * 
     * @return Lista dei file con statistiche
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listDocuments() {
        try {
            Map<String, Object> result = documentProcessingService.listIndexedDocuments();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ Errore nel recupero della lista documenti", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Errore nel recupero della lista documenti",
                        "details", e.getMessage()
                    ));
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "Document Processing API"
        ));
    }
}
