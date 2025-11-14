package com.example.rag.camel;

import com.example.rag.service.DocumentProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Bean Spring che processa i file intercettati da Apache Camel.
 * 
 * Converte il File del file system in un formato che DocumentProcessingService
 * può processare, quindi lo invia a Qdrant per l'indicizzazione.
 */
@Component("fileProcessorBean")
@Slf4j
@RequiredArgsConstructor
public class FileProcessorBean {

    private final DocumentProcessingService documentProcessingService;

    /**
     * Processa un file dal file system e lo indicizza in Qdrant
     * 
     * @param exchange Camel Exchange contenente il file
     * @throws Exception se il processamento fallisce
     */
    public void processFile(Exchange exchange) throws Exception {
        File file = exchange.getIn().getBody(File.class);
        String filename = exchange.getIn().getHeader("CamelFileName", String.class);
        
        log.info("🔄 Inizio processamento file: {} ({} bytes)", 
                filename, file.length());

        try {
            // Crea un MultipartFile wrapper per usare DocumentProcessingService
            MultipartFile multipartFile = new FileSystemMultipartFile(file, filename);
            
            // Processa il documento
            Map<String, Object> result = documentProcessingService.processDocument(multipartFile);
            
            log.info("✅ File processato con successo: {}", filename);
            log.info("📊 Statistiche: chunks={}, embedding_dim={}", 
                    result.get("chunks_created"), 
                    result.get("embedding_dimension"));
            
            // Aggiungi il risultato all'exchange per eventuali usi successivi
            exchange.getIn().setHeader("ProcessingResult", result);
            
        } catch (Exception e) {
            log.error("❌ Errore nel processamento di {}: {}", filename, e.getMessage());
            throw e; // Rilancia per gestione errori della route
        }
    }

    /**
     * Implementazione semplice di MultipartFile per file del file system
     */
    private static class FileSystemMultipartFile implements MultipartFile {
        private final File file;
        private final String originalFilename;

        public FileSystemMultipartFile(File file, String originalFilename) {
            this.file = file;
            this.originalFilename = originalFilename;
        }

        @Override
        public String getName() {
            return file.getName();
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            // Determina content type in base all'estensione
            String lower = originalFilename.toLowerCase();
            if (lower.endsWith(".pdf")) return "application/pdf";
            if (lower.endsWith(".doc")) return "application/msword";
            if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
            if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            if (lower.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
            if (lower.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            if (lower.endsWith(".txt")) return "text/plain";
            if (lower.endsWith(".html")) return "text/html";
            if (lower.endsWith(".xml")) return "application/xml";
            return "application/octet-stream";
        }

        @Override
        public boolean isEmpty() {
            return file.length() == 0;
        }

        @Override
        public long getSize() {
            return file.length();
        }

        @Override
        public byte[] getBytes() throws IOException {
            try (InputStream inputStream = new FileInputStream(file)) {
                return inputStream.readAllBytes();
            }
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new FileInputStream(file);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            // Non necessario per il nostro caso d'uso
            throw new UnsupportedOperationException("transferTo not supported");
        }
    }
}
