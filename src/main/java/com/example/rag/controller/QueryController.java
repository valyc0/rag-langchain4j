package com.example.rag.controller;

import com.example.rag.service.RagQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller per query RAG
 */
@RestController
@RequestMapping("/api/query")
@RequiredArgsConstructor
@Slf4j
public class QueryController {

    private final RagQueryService ragQueryService;

    /**
     * Esegui una query RAG
     * 
     * GET /api/query?question=La+mia+domanda
     * 
     * @param question La domanda da fare sui documenti
     * @return Risposta con fonti
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> query(
            @RequestParam("question") String question) {
        
        try {
            if (question == null || question.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "La domanda non può essere vuota"));
            }
            
            log.info("❓ Query ricevuta: {}", question);
            Map<String, Object> result = ragQueryService.query(question);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("❌ Errore durante la query", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Errore durante l'elaborazione della query",
                        "details", e.getMessage()
                    ));
        }
    }

    /**
     * Esegui una query RAG (POST version con JSON body)
     * 
     * POST /api/query
     * Content-Type: application/json
     * Body: {"question": "La mia domanda"}
     * 
     * @param request Mappa con la domanda
     * @return Risposta con fonti
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> queryPost(
            @RequestBody Map<String, String> request) {
        
        try {
            String question = request.get("question");
            
            if (question == null || question.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "La domanda non può essere vuota"));
            }
            
            log.info("❓ Query ricevuta (POST): {}", question);
            Map<String, Object> result = ragQueryService.query(question);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("❌ Errore durante la query", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Errore durante l'elaborazione della query",
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
            "service", "RAG Query API"
        ));
    }
}
