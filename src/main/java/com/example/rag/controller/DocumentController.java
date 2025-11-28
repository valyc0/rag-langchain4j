package com.example.rag.controller;

import com.example.rag.model.DocumentInfo;
import com.example.rag.service.DocumentProcessingService;
import com.example.rag.service.DocumentStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
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
    private final DocumentStatusService documentStatusService;

    /**
     * Upload e indicizza un documento in modo asincrono
     * 
     * POST /api/documents/upload
     * Content-Type: multipart/form-data
     * 
     * @param file Il file da caricare (PDF, Word, Excel, ecc.)
     * @return Messaggio di conferma con stato PROCESSING
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
            
            String filename = file.getOriginalFilename();
            
            // Registra il documento in stato PROCESSING
            documentStatusService.registerDocument(filename);
            
            // Avvia il processamento asincrono
            byte[] content = file.getBytes();
            documentProcessingService.processDocumentAsync(filename, content);
            
            // Ritorna immediatamente con status PROCESSING
            return ResponseEntity.ok(Map.of(
                "message", "✅ Upload completato! Il documento è in elaborazione...",
                "data", Map.of(
                    "filename", filename,
                    "size_bytes", file.getSize(),
                    "status", "PROCESSING"
                )
            ));
            
        } catch (IOException e) {
            log.error("❌ Errore durante la lettura del file: {}", file.getOriginalFilename(), e);
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Errore di lettura del file";
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "message", "❌ Errore durante la lettura del file",
                        "error", errorMsg
                    ));
        } catch (Exception e) {
            log.error("❌ Errore durante l'upload del documento", e);
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Errore sconosciuto";
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "message", "❌ Errore durante il processamento del documento",
                        "error", errorMsg
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
     * Ottiene lo stato di un documento specifico
     * 
     * GET /api/documents/status/{filename}
     * 
     * @param filename Il nome del file
     * @return Informazioni sullo stato del documento
     */
    @GetMapping("/status/{filename}")
    public ResponseEntity<Map<String, Object>> getDocumentStatus(
            @PathVariable("filename") String filename) {
        
        try {
            DocumentInfo info = documentStatusService.getDocumentStatus(filename);
            
            if (info == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                            "error", "Documento non trovato",
                            "filename", filename
                        ));
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("filename", info.getFilename());
            response.put("status", info.getStatus().toString());
            response.put("chunks", info.getChunks());
            response.put("uploadTimestamp", info.getUploadTimestamp());
            
            if (info.getReadyTimestamp() != null) {
                response.put("readyTimestamp", info.getReadyTimestamp());
            }
            
            if (info.getErrorMessage() != null) {
                response.put("errorMessage", info.getErrorMessage());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Errore nel recupero dello stato del documento", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Errore nel recupero dello stato",
                        "details", e.getMessage()
                    ));
        }
    }
    
    /**
     * Ottiene lo stato di tutti i documenti
     * 
     * GET /api/documents/statuses
     * 
     * @return Mappa con lo stato di tutti i documenti
     */
    @GetMapping("/statuses")
    public ResponseEntity<Map<String, Object>> getAllStatuses() {
        try {
            Map<String, DocumentInfo> statuses = documentStatusService.getAllStatuses();
            
            Map<String, Object> response = new HashMap<>();
            for (Map.Entry<String, DocumentInfo> entry : statuses.entrySet()) {
                DocumentInfo info = entry.getValue();
                Map<String, Object> docInfo = new HashMap<>();
                docInfo.put("status", info.getStatus().toString());
                docInfo.put("chunks", info.getChunks());
                docInfo.put("uploadTimestamp", info.getUploadTimestamp());
                
                if (info.getReadyTimestamp() != null) {
                    docInfo.put("readyTimestamp", info.getReadyTimestamp());
                }
                
                if (info.getErrorMessage() != null) {
                    docInfo.put("errorMessage", info.getErrorMessage());
                }
                
                response.put(entry.getKey(), docInfo);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Errore nel recupero degli stati", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Errore nel recupero degli stati",
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
