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
     * Cancella un documento indicizzato
     * 
     * DELETE /api/documents/{filename}
     * 
     * @param filename Il nome del file da cancellare
     * @return Risultato della cancellazione
     */
    @DeleteMapping("/{filename}")
    public ResponseEntity<Map<String, Object>> deleteDocument(
            @PathVariable("filename") String filename) {
        
        try {
            log.info("🗑️ Richiesta cancellazione documento: {}", filename);
            
            // Validazione
            if (filename == null || filename.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Nome file non valido"));
            }
            
            // Cancella il documento
            Map<String, Object> result = documentProcessingService.deleteDocument(filename);
            
            String status = (String) result.get("status");
            if ("not_found".equals(status)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
            } else if ("error".equals(status)) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("❌ Errore durante la cancellazione del documento", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Errore durante la cancellazione del documento",
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
