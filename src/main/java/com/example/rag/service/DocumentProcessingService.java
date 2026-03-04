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
import java.util.ArrayList;
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
     * Divide il testo in chunks con overlap e aggiunge metadati (filename, timestamp, chunk_index)
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
        List<TextSegment> segments = splitter.split(document);

        // Aggiunge chunk_index a ogni segmento per tracciabilità e ordinamento
        List<TextSegment> indexedSegments = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            TextSegment seg = segments.get(i);
            dev.langchain4j.data.document.Metadata enriched = seg.metadata()
                    .put("chunk_index", i)
                    .put("chunk_total", segments.size());
            indexedSegments.add(TextSegment.from(seg.text(), enriched));
        }
        return indexedSegments;
    }

    /**
     * Genera embeddings per i chunks
     * Usa AllMiniLmL6V2 (locale, gratis, 384 dimensioni)
     */
    private List<Embedding> generateEmbeddings(List<TextSegment> chunks) {
        return embeddingModel.embedAll(chunks).content();
    }

    /**
     * Lista tutti i documenti indicizzati usando la Scroll API di Qdrant.
     * Itera su tutti i punti della collection in pagine da 250 per estrarre i metadata
     * senza dover fare una ricerca semantica con embedding fittizio.
     */
    public Map<String, Object> listIndexedDocuments() {
        log.info("📋 Recupero lista documenti indicizzati (scroll Qdrant)");

        try {
            Map<String, Integer> fileStats = new HashMap<>();
            Map<String, Long> fileTimestamps = new HashMap<>();
            io.qdrant.client.grpc.Points.PointId offset = null;
            final int PAGE_SIZE = 250;
            int totalPoints = 0;

            do {
                ScrollPoints.Builder builder = ScrollPoints.newBuilder()
                        .setCollectionName(collectionName)
                        .setLimit(PAGE_SIZE)
                        .setWithPayload(io.qdrant.client.grpc.Points.WithPayloadSelector.newBuilder()
                                .setEnable(true)
                                .build());
                if (offset != null) {
                    builder.setOffset(offset);
                }

                ScrollResponse response = qdrantClient.scrollAsync(builder.build()).get();
                List<io.qdrant.client.grpc.Points.RetrievedPoint> points = response.getResultList();
                totalPoints += points.size();

                for (io.qdrant.client.grpc.Points.RetrievedPoint point : points) {
                    var payload = point.getPayloadMap();
                    // LangChain4j 0.35 usa struttura FLAT: "filename", "upload_timestamp" direttamente nel payload
                    var filenameVal = payload.get("filename");
                    var tsVal = payload.get("upload_timestamp");
                    if (filenameVal != null && filenameVal.hasStringValue()) {
                        String filename = filenameVal.getStringValue();
                        if (!filename.isEmpty()) {
                            fileStats.merge(filename, 1, Integer::sum);
                            if (tsVal != null && !fileTimestamps.containsKey(filename)) {
                                long ts = tsVal.hasIntegerValue()
                                        ? tsVal.getIntegerValue()
                                        : (long) tsVal.getDoubleValue();
                                fileTimestamps.put(filename, ts);
                            }
                        }
                    }
                }

                // Imposta l'offset per la pagina successiva
                if (!response.hasNextPageOffset()) {
                    offset = null;
                } else {
                    offset = response.getNextPageOffset();
                }

            } while (offset != null);

            log.info("✅ Trovati {} documenti unici su {} chunks totali", fileStats.size(), totalPoints);

            return Map.of(
                "total_documents", fileStats.size(),
                "total_chunks", totalPoints,
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
