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

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private int topK; // Numero di chunks candidati da recuperare

    @Value("${rag.min-score:0.45}")
    private double minScore; // Score minimo per filtrare chunks irrilevanti

    @Value("${rag.max-chunks-per-doc:4}")
    private int maxChunksPerDoc; // Limite di chunks per singolo documento

    // Configurazione LLM per logging
    @Value("${llm.provider:gemini}")
    private String llmProvider;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String geminiModel;

    @Value("${ollama.model:llama3.2}")
    private String ollamaModel;

    @Value("${openrouter.model:anthropic/claude-3-haiku}")
    private String openRouterModel;

    /**
     * Esegue una query RAG completa
     */
    public Map<String, Object> query(String question) {
        // Log del modello LLM in uso
        String currentModel = getCurrentModelName();
        log.info("❓ Query ricevuta: {}", question);
        log.info("🤖 LLM Provider: {} | Modello: {}", llmProvider.toUpperCase(), currentModel);
        
        // 1. Genera embedding della domanda
        Embedding questionEmbedding = embeddingModel.embed(question).content();
        log.debug("🔢 Embedding domanda generato: {} dimensioni", questionEmbedding.dimension());
        
        // 2. Cerca chunks simili in Qdrant (prendiamo più candidati per poi filtrare)
        List<EmbeddingMatch<TextSegment>> candidates = 
                embeddingStore.findRelevant(questionEmbedding, topK);
        
        if (candidates.isEmpty()) {
            log.warn("⚠️ Nessun documento trovato in Qdrant");
            return Map.of(
                "answer", "Non ho trovato documenti per rispondere a questa domanda. " +
                         "Carica prima alcuni documenti!",
                "sources", List.of(),
                "question", question
            );
        }

        // 3. Filtra per score minimo (rimuove chunks irrilevanti)
        List<EmbeddingMatch<TextSegment>> filtered = candidates.stream()
                .filter(m -> m.score() >= minScore)
                .collect(Collectors.toList());

        log.info("📊 Chunks: {} candidati, {} sopra soglia min-score={}", 
                candidates.size(), filtered.size(), minScore);

        if (filtered.isEmpty()) {
            log.warn("⚠️ Tutti i chunks sotto la soglia di score {}", minScore);
            return Map.of(
                "answer", "Non ho trovato informazioni sufficientemente rilevanti nei documenti per rispondere a questa domanda.",
                "sources", List.of(),
                "question", question,
                "chunks_used", 0
            );
        }

        // 4. Limita il numero di chunks per documento (evita che un file domini tutto il contesto)
        List<EmbeddingMatch<TextSegment>> relevantChunks = applyPerDocumentLimit(filtered);
        
        log.info("📚 Chunks finali nel contesto: {} (dopo limite {}/doc)", 
                relevantChunks.size(), maxChunksPerDoc);

        // Log degli score per debug
        relevantChunks.forEach(match -> 
            log.debug("📊 Score: {:.3f}, File: {}", 
                match.score(), 
                match.embedded().metadata("filename"))
        );
        
        // 5. Estrai il testo e crea il contesto (con metadata di qualità)
        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < relevantChunks.size(); i++) {
            EmbeddingMatch<TextSegment> match = relevantChunks.get(i);
            String filename = match.embedded().metadata("filename");
            Integer chunkIdx = match.embedded().metadata().getInteger("chunk_index");
            String chunkRef = chunkIdx != null ? " #" + chunkIdx : "";
            contextBuilder.append(String.format("[Fonte %d: %s%s | rilevanza: %.2f]\n%s",
                    i + 1, filename, chunkRef, match.score(), match.embedded().text()));
            if (i < relevantChunks.size() - 1) {
                contextBuilder.append("\n\n---\n\n");
            }
        }
        String context = contextBuilder.toString();
        
        // 6. Costruisci il prompt per LLM
        String prompt = buildPrompt(context, question);
        log.debug("📝 Prompt costruito: {} caratteri", prompt.length());
        
        // 7. Chiedi all'LLM
        String answer;
        try {
            long startTime = System.currentTimeMillis();
            answer = chatLanguageModel.generate(prompt);
            long duration = System.currentTimeMillis() - startTime;
            log.info("✅ Risposta generata da {} ({}) in {}ms: {} caratteri", 
                llmProvider.toUpperCase(), currentModel, duration, answer.length());
        } catch (Exception e) {
            log.error("❌ Errore chiamata LLM ({})", llmProvider, e);
            answer = "Errore nella generazione della risposta. Il prompt potrebbe essere troppo lungo o ci sono problemi con l'API " + llmProvider + ".";
        }
        
        // 8. Prepara le fonti (sources) con score
        List<Map<String, Object>> sources = relevantChunks.stream()
                .map(match -> {
                    Map<String, Object> source = new java.util.HashMap<>();
                    source.put("text", match.embedded().text());
                    source.put("score", match.score());
                    source.put("filename", match.embedded().metadata("filename"));
                    Integer chunkIdx = match.embedded().metadata().getInteger("chunk_index");
                    if (chunkIdx != null) source.put("chunk_index", chunkIdx);
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
     * Limita il numero di chunks per documento per evitare che un singolo file
     * domini tutto il contesto della risposta. L'ordine per score viene mantenuto.
     */
    private List<EmbeddingMatch<TextSegment>> applyPerDocumentLimit(
            List<EmbeddingMatch<TextSegment>> sorted) {
        Map<String, Integer> docCount = new LinkedHashMap<>();
        List<EmbeddingMatch<TextSegment>> result = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : sorted) {
            String filename = match.embedded().metadata("filename");
            if (filename == null) filename = "unknown";
            int count = docCount.getOrDefault(filename, 0);
            if (count < maxChunksPerDoc) {
                result.add(match);
                docCount.put(filename, count + 1);
            }
        }
        return result;
    }

    /**
     * Costruisce il prompt con il contesto e la domanda
     */
    private String buildPrompt(String context, String question) {
        return String.format("""
            Sei un assistente esperto che risponde a domande basandosi ESCLUSIVAMENTE sulle informazioni fornite nel contesto.

            ISTRUZIONI:
            1. Leggi attentamente TUTTO il contesto prima di rispondere
            2. Rispondi SOLO usando informazioni presenti nel contesto (citando la fonte quando possibile)
            3. Se la risposta richiede di unire informazioni da più fonti, fallo in modo coerente
            4. Se l'informazione NON è nel contesto, rispondi esattamente: "Non trovo questa informazione nei documenti caricati"
            5. NON inventare, NON dedurre, NON aggiungere conoscenze esterne
            6. Fornisci risposte complete, strutturate e facili da leggere
            7. Se ci sono informazioni contrastanti tra le fonti, segnalalo esplicitamente

            CONTESTO (estratto dai documenti indicizzati):
            %s

            DOMANDA: %s

            RISPOSTA (basata esclusivamente sul contesto sopra):
            """, context, question);
    }

    /**
     * Query semplificata che ritorna solo la risposta (senza metadata)
     */
    public String querySimple(String question) {
        Map<String, Object> result = query(question);
        return (String) result.get("answer");
    }

    /**
     * Restituisce il nome del modello corrente basato sul provider configurato
     */
    private String getCurrentModelName() {
        return switch (llmProvider.toLowerCase()) {
            case "gemini" -> geminiModel;
            case "ollama" -> ollamaModel;
            case "openrouter" -> openRouterModel;
            default -> "unknown";
        };
    }

    /**
     * Restituisce informazioni sul modello LLM corrente
     */
    public String getCurrentModelInfo() {
        return llmProvider + "/" + getCurrentModelName();
    }
}
