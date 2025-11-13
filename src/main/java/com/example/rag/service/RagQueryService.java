package com.example.rag.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service per gestire query RAG (Retrieval-Augmented Generation)
 * 1. Trasforma la domanda in embedding
 * 2. Cerca chunks simili in Qdrant
 * 3. Costruisce il prompt con il contesto
 * 4. Chiede a Gemini la risposta
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RagQueryService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final ChatLanguageModel chatLanguageModel;

    @Value("${rag.top-k:10}")
    private int topK; // Numero di chunks da recuperare (configurabile)

    /**
     * Esegue una query RAG completa
     */
    public Map<String, Object> query(String question) {
        log.info("❓ Query ricevuta: {}", question);
        
        // 1. Genera embedding della domanda
        Embedding questionEmbedding = embeddingModel.embed(question).content();
        log.debug("🔢 Embedding domanda generato: {} dimensioni", questionEmbedding.dimension());
        
        // 2. Cerca chunks simili in Qdrant (prendiamo più risultati)
        List<EmbeddingMatch<TextSegment>> relevantChunks = 
                embeddingStore.findRelevant(questionEmbedding, topK);
        
        if (relevantChunks.isEmpty()) {
            log.warn("⚠️ Nessun documento trovato in Qdrant");
            return Map.of(
                "answer", "Non ho trovato documenti per rispondere a questa domanda. " +
                         "Carica prima alcuni documenti!",
                "sources", List.of(),
                "question", question
            );
        }
        
        log.info("📚 Trovati {} chunks rilevanti", relevantChunks.size());
        
        // Log degli score per debug
        relevantChunks.forEach(match -> 
            log.debug("📊 Score: {}, File: {}", 
                match.score(), 
                match.embedded().metadata("filename"))
        );
        
        // 3. Estrai il testo e crea il contesto
        String context = relevantChunks.stream()
                .map(match -> {
                    String filename = match.embedded().metadata("filename");
                    return String.format("[Fonte: %s]\n%s", filename, match.embedded().text());
                })
                .collect(Collectors.joining("\n\n---\n\n"));
        
        // 4. Costruisci il prompt per Gemini
        String prompt = buildPrompt(context, question);
        log.debug("📝 Prompt costruito: {} caratteri", prompt.length());
        
        // 5. Chiedi a Gemini
        String answer;
        try {
            answer = chatLanguageModel.generate(prompt);
            log.info("✅ Risposta generata da Gemini: {} caratteri", answer.length());
        } catch (Exception e) {
            log.error("❌ Errore chiamata Gemini", e);
            answer = "Errore nella generazione della risposta. Il prompt potrebbe essere troppo lungo o ci sono problemi con l'API Gemini.";
        }
        
        // 6. Prepara le fonti (sources) con score
        List<Map<String, Object>> sources = relevantChunks.stream()
                .map(match -> {
                    Map<String, Object> source = new java.util.HashMap<>();
                    source.put("text", match.embedded().text());
                    source.put("score", match.score());
                    source.put("filename", match.embedded().metadata("filename"));
                    return source;
                })
                .collect(Collectors.toList());
        
        return Map.of(
            "answer", answer,
            "sources", sources,
            "question", question,
            "chunks_used", relevantChunks.size()
        );
    }

    /**
     * Costruisce il prompt per Gemini con contesto e domanda
     */
    private String buildPrompt(String context, String question) {
        return String.format("""
            Sei un assistente intelligente che risponde a domande basandoti ESCLUSIVAMENTE sulle informazioni fornite nel contesto.
            
            REGOLE IMPORTANTI:
            - Rispondi SOLO utilizzando le informazioni esplicite nel contesto fornito
            - Se la risposta è nel contesto, citala in modo preciso e completo
            - Se la risposta NON è nel contesto, rispondi: "Non trovo questa informazione nei documenti caricati"
            - NON inventare, NON dedurre, NON aggiungere informazioni esterne
            - Leggi TUTTO il contesto attentamente prima di rispondere
            - Se trovi la risposta, forniscila in modo chiaro e diretto
            
            CONTESTO (da documenti caricati):
            %s
            
            DOMANDA DELL'UTENTE: %s
            
            RISPOSTA (basata SOLO sul contesto sopra):
            """, context, question);
    }

    /**
     * Query semplificata che ritorna solo la risposta (senza metadata)
     */
    public String querySimple(String question) {
        Map<String, Object> result = query(question);
        return (String) result.get("answer");
    }
}
