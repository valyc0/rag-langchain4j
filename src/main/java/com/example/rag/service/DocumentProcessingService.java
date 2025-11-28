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
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.ScrollPoints;
import io.qdrant.client.grpc.Points.ScrollResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
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
import java.util.stream.Collectors;

import static io.qdrant.client.ConditionFactory.matchKeyword;

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
    private final QdrantClient qdrantClient;
    private final DocumentStatusService documentStatusService;

    @Value("${rag.chunk-size:300}")
    private int chunkSize;  // Caratteri per chunk (configurabile)
    
    @Value("${rag.chunk-overlap:50}")
    private int chunkOverlap; // Overlap tra chunks (configurabile)
    
    @Value("${qdrant.collection-name:documenti}")
    private String collectionName;
    
    public DocumentProcessingService(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            QdrantClient qdrantClient,
            DocumentStatusService documentStatusService) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.qdrantClient = qdrantClient;
        this.documentStatusService = documentStatusService;
    }

    /**
     * Processa e indicizza un documento in modo asincrono
     */
    @Async("documentProcessingExecutor")
    public void processDocumentAsync(String filename, byte[] fileContent) {
        log.info("📄 Inizio processamento asincrono documento: {}", filename);
        
        Path tempFile = null;
        try {
            // 1. Salva temporaneamente il file
            tempFile = saveTempFile(filename, fileContent);
            
            // 2. Estrai il testo con Apache Tika
            String text = extractText(tempFile);
            log.info("✅ Testo estratto: {} caratteri", text.length());
            
            // 3. Dividi in chunks
            List<TextSegment> chunks = splitIntoChunks(text, filename);
            log.info("✂️ Documento diviso in {} chunks", chunks.size());
            
            // 4. Genera embeddings
            List<Embedding> embeddings = generateEmbeddings(chunks);
            log.info("🔢 Embeddings generati: {} vettori di {} dimensioni", 
                    embeddings.size(), embeddings.get(0).dimension());
            
            // 5. Salva in Qdrant
            embeddingStore.addAll(embeddings, chunks);
            log.info("💾 Salvato in Qdrant!");
            
            // 6. Marca come READY
            documentStatusService.markReady(filename, chunks.size());
            
        } catch (Exception e) {
            log.error("❌ Errore durante il processamento asincrono: {}", filename, e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Errore sconosciuto";
            documentStatusService.markError(filename, errorMessage);
        } finally {
            // 7. Pulisci file temporaneo
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("⚠️ Impossibile eliminare file temporaneo: {}", tempFile);
                }
            }
        }
    }
    
    /**
     * Processa e indicizza un documento (sincrono - legacy)
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
            
            // 4. Genera embeddings
            List<Embedding> embeddings = generateEmbeddings(chunks);
            log.info("🔢 Embeddings generati: {} vettori di {} dimensioni", 
                    embeddings.size(), embeddings.get(0).dimension());
            
            // 5. Salva in Qdrant
            embeddingStore.addAll(embeddings, chunks);
            log.info("💾 Salvato in Qdrant!");
            
            // 6. Ritorna statistiche
            return Map.of(
                "filename", file.getOriginalFilename(),
                "size_bytes", file.getSize(),
                "text_length", text.length(),
                "chunks_created", chunks.size(),
                "embedding_dimension", embeddings.get(0).dimension(),
                "status", "success"
            );
            
        } finally {
            // 7. Pulisci file temporaneo
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Salva il file temporaneamente da MultipartFile
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
     * Salva il file temporaneamente da byte array
     */
    private Path saveTempFile(String filename, byte[] content) throws IOException {
        Path tempFile = Files.createTempFile("upload-", "-" + filename);
        Files.write(tempFile, content);
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
            String text = document.text();
            
            // Verifica che il testo non sia vuoto o solo whitespace
            if (text == null || text.trim().isEmpty()) {
                throw new IOException(
                    "Il documento non contiene testo estraibile. " +
                    "Potrebbe essere un'immagine scannerizzata, protetto da password, " +
                    "o in un formato non supportato."
                );
            }
            
            return text;
        } catch (dev.langchain4j.data.document.BlankDocumentException e) {
            // Rilancia come IOException con messaggio descrittivo
            throw new IOException(
                "Il documento non contiene testo estraibile. " +
                "Potrebbe essere un'immagine scannerizzata, protetto da password, " +
                "o in un formato non supportato.", e
            );
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
     * Cancella tutti i chunks di un documento da Qdrant
     */
    public Map<String, Object> deleteDocument(String filename) {
        log.info("🗑️ Inizio cancellazione documento: {}", filename);
        
        try {
            // Usa l'API Qdrant per cancellare i punti filtrando per metadata
            Filter filter = Filter.newBuilder()
                .addMust(matchKeyword("filename", filename))
                .build();
            
            // Recupera gli ID dei punti da cancellare
            ScrollPoints scrollRequest = ScrollPoints.newBuilder()
                .setCollectionName(collectionName)
                .setFilter(filter)
                .setLimit(1000)
                .setWithPayload(io.qdrant.client.grpc.Points.WithPayloadSelector.newBuilder()
                    .setEnable(false)
                    .build())
                .build();
            
            ScrollResponse scrollResponse = qdrantClient.scrollAsync(scrollRequest).get();
            List<io.qdrant.client.grpc.Points.PointId> pointIds = scrollResponse.getResultList()
                .stream()
                .map(point -> point.getId())
                .collect(Collectors.toList());
            
            if (pointIds.isEmpty()) {
                log.warn("⚠️ Nessun chunk trovato per il documento: {}", filename);
                return Map.of(
                    "status", "not_found",
                    "message", "Documento non trovato",
                    "filename", filename
                );
            }
            
            // Cancella i punti per ID
            qdrantClient.deleteAsync(collectionName, pointIds).get();
            
            log.info("✅ Documento cancellato: {} ({} chunks rimossi)", filename, pointIds.size());
            
            return Map.of(
                "status", "success",
                "message", "Documento cancellato con successo",
                "filename", filename,
                "chunks_deleted", pointIds.size()
            );
            
        } catch (Exception e) {
            log.error("❌ Errore durante la cancellazione del documento: {}", filename, e);
            return Map.of(
                "status", "error",
                "message", "Errore durante la cancellazione",
                "filename", filename,
                "error", e.getMessage()
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
