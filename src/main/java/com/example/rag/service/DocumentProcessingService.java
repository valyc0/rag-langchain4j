package com.example.rag.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service per processare documenti:
 * 1. Estrae il testo (PDF, Word, Excel, ecc.) con Apache Tika
 * 2. Divide il testo in chunks
 * 3. Genera embeddings
 * 4. Salva in Qdrant
 */
@Service
@Slf4j
public class DocumentProcessingService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    @Value("${rag.chunk-size:300}")
    private int chunkSize;  // Caratteri per chunk (configurabile)
    
    @Value("${rag.chunk-overlap:50}")
    private int chunkOverlap; // Overlap tra chunks (configurabile)
    
    @Value("${rag.batch-size:50}")
    private int batchSize; // Numero di chunks da processare per batch
    
    public DocumentProcessingService(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    /**
     * Processa e indicizza un documento
     */
    public Map<String, Object> processDocument(MultipartFile file) throws IOException {
        log.info("📄 Inizio processamento documento: {}", file.getOriginalFilename());
        
        // 1. Salva temporaneamente il file
        Path tempFile = saveTempFile(file);
        
        try {
            // 2. Estrai il testo con Apache Tika
            String text = extractText(tempFile);
            log.info("✅ Testo estratto: {} caratteri", text.length());
            
            // 3. Dividi in chunks
            List<TextSegment> chunks = splitIntoChunks(text, file.getOriginalFilename());
            log.info("✂️ Documento diviso in {} chunks", chunks.size());
            
            // 4. Genera embeddings e salva a BATCH (ottimizzato per file grandi)
            int totalChunks = chunks.size();
            int embeddingDimension = 0;
            
            log.info("🔄 Inizio processamento a batch di '{}' (batch size: {})", 
                     file.getOriginalFilename(), batchSize);
            
            for (int i = 0; i < totalChunks; i += batchSize) {
                int endIndex = Math.min(i + batchSize, totalChunks);
                List<TextSegment> batch = chunks.subList(i, endIndex);
                
                log.info("📦 [{}] Processamento batch {}/{}: chunks {}-{}", 
                         file.getOriginalFilename(),
                         (i / batchSize) + 1, 
                         (totalChunks + batchSize - 1) / batchSize,
                         i, 
                         endIndex - 1);
                
                // Genera embeddings per questo batch
                List<Embedding> batchEmbeddings = embeddingModel.embedAll(batch).content();
                
                if (i == 0) {
                    embeddingDimension = batchEmbeddings.get(0).dimension();
                }
                
                // Salva questo batch in Qdrant
                embeddingStore.addAll(batchEmbeddings, batch);
                
                log.info("✅ [{}] Batch {}/{} salvato: {} chunks", 
                         file.getOriginalFilename(),
                         (i / batchSize) + 1,
                         (totalChunks + batchSize - 1) / batchSize,
                         batch.size());
            }
            
            log.info("💾 [{}] Tutti i chunks salvati in Qdrant (processati a batch)!",
                     file.getOriginalFilename());
            
            // 6. Ritorna statistiche
            return Map.of(
                "filename", file.getOriginalFilename(),
                "size_bytes", file.getSize(),
                "text_length", text.length(),
                "chunks_created", chunks.size(),
                "embedding_dimension", embeddingDimension,
                "batches_processed", (totalChunks + batchSize - 1) / batchSize,
                "batch_size", batchSize,
                "status", "success"
            );
            
        } finally {
            // 7. Pulisci file temporaneo
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Salva il file temporaneamente
     */
    private Path saveTempFile(MultipartFile file) throws IOException {
        Path tempFile = Files.createTempFile("upload-", "-" + file.getOriginalFilename());
        
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        
        log.debug("📁 File salvato temporaneamente: {}", tempFile);
        return tempFile;
    }

    /**
     * Estrae testo dal documento usando Apache Tika
     * Supporta: PDF, Word, Excel, PowerPoint, TXT, HTML, ecc.
     */
    private String extractText(Path filePath) throws IOException {
        DocumentParser parser = new ApacheTikaDocumentParser();
        
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            Document document = parser.parse(inputStream);
            return document.text();
        }
    }

    /**
     * Divide il testo in chunks con overlap
     */
    private List<TextSegment> splitIntoChunks(String text, String filename) {
        DocumentSplitter splitter = DocumentSplitters.recursive(
                chunkSize,
                chunkOverlap
        );
        
        // Crea un documento con metadata
        dev.langchain4j.data.document.Metadata metadata = dev.langchain4j.data.document.Metadata.from("filename", filename)
                .put("upload_timestamp", System.currentTimeMillis());
        
        Document document = Document.from(text, metadata);
        
        return splitter.split(document);
    }

    /**
     * Genera embeddings per i chunks
     * Usa AllMiniLmL6V2 (locale, gratis, 384 dimensioni)
     */
    private List<Embedding> generateEmbeddings(List<TextSegment> chunks) {
        return embeddingModel.embedAll(chunks).content();
    }

    /**
     * Lista tutti i documenti indicizzati recuperando i metadata da Qdrant
     */
    public Map<String, Object> listIndexedDocuments() {
        log.info("📋 Recupero lista documenti indicizzati");
        
        try {
            // Recupera alcuni chunk per estrarre i metadata
            // Qdrant non ha un'API diretta per listare metadata unici, quindi facciamo una ricerca generica
            dev.langchain4j.data.message.ChatMessage dummyMessage = 
                dev.langchain4j.data.message.UserMessage.from("list");
            Embedding dummyEmbedding = embeddingModel.embed(dummyMessage.text()).content();
            
            // Recupera molti risultati per avere una panoramica
            List<dev.langchain4j.store.embedding.EmbeddingMatch<TextSegment>> matches = 
                embeddingStore.findRelevant(dummyEmbedding, 100);
            
            // Estrai i filename unici e conta i chunks
            Map<String, Integer> fileStats = new HashMap<>();
            Map<String, Long> fileTimestamps = new HashMap<>();
            
            for (var match : matches) {
                TextSegment segment = match.embedded();
                if (segment != null && segment.metadata() != null) {
                    String filename = segment.metadata().getString("filename");
                    Long timestamp = segment.metadata().getLong("upload_timestamp");
                    
                    if (filename != null) {
                        fileStats.put(filename, fileStats.getOrDefault(filename, 0) + 1);
                        if (timestamp != null && !fileTimestamps.containsKey(filename)) {
                            fileTimestamps.put(filename, timestamp);
                        }
                    }
                }
            }
            
            log.info("✅ Trovati {} documenti unici", fileStats.size());
            
            return Map.of(
                "total_documents", fileStats.size(),
                "total_chunks", matches.size(),
                "documents", fileStats,
                "timestamps", fileTimestamps
            );
            
        } catch (Exception e) {
            log.error("❌ Errore nel recupero documenti", e);
            return Map.of(
                "error", "Impossibile recuperare la lista documenti",
                "details", e.getMessage()
            );
        }
    }

    /**
     * Verifica se un file è supportato (opzionale)
     */
    public boolean isSupportedFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".pdf") 
            || lower.endsWith(".doc") 
            || lower.endsWith(".docx")
            || lower.endsWith(".txt")
            || lower.endsWith(".xlsx")
            || lower.endsWith(".xls")
            || lower.endsWith(".pptx")
            || lower.endsWith(".ppt")
            || lower.endsWith(".html")
            || lower.endsWith(".xml");
    }
}
