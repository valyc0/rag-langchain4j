package com.example.rag.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
/**
 * Configurazione per Qdrant Vector Database.
 * Gestisce anche la creazione automatica della collection all'avvio
 * con la dimensione corretta in base all'embedding model configurato.
 */
@Configuration
@Slf4j
public class QdrantConfig {

    @Value("${qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.port:6334}")
    private int qdrantPort;

    @Value("${qdrant.collection-name:documenti}")
    private String collectionName;

    @Value("${qdrant.use-tls:false}")
    private boolean useTls;

    @Value("${embedding.dimension:384}")
    private int embeddingDimension;

    @Bean
    public QdrantClient qdrantClient() {
        return new QdrantClient(
            QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, useTls)
                .build()
        );
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        QdrantEmbeddingStore delegate = QdrantEmbeddingStore.builder()
                .host(qdrantHost)
                .port(qdrantPort)
                .collectionName(collectionName)
                .useTls(useTls)
                .build();
        // Usa il wrapper che risolve il bug di LangChain4j 0.35.0:
        // QdrantEmbeddingStore.findRelevant ricalcola cosine similarity con il vettore
        // restituito da Qdrant (vuoto per default) → crash. Il wrapper usa lo score Qdrant direttamente.
        return new CustomQdrantEmbeddingStore(delegate, qdrantClient(), collectionName);
    }

    /**
    /**
     * Crea la collection Qdrant all'avvio se non esiste.
     * Usa la dimensione configurata in embedding.dimension.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initCollection() {
        try {
            var existingCollections = qdrantClient().listCollectionsAsync().get();
            boolean exists = existingCollections.stream()
                    .anyMatch(c -> c.equals(collectionName));

            if (exists) {
                log.info("✅ Collection Qdrant '{}' già esistente (dim configurata: {})",
                        collectionName, embeddingDimension);
                return;
            }

            // Crea la collection con la dimensione corretta
            qdrantClient().createCollectionAsync(
                    collectionName,
                    VectorParams.newBuilder()
                            .setSize(embeddingDimension)
                            .setDistance(Distance.Cosine)
                            .build()
            ).get();
            log.info("✅ Collection Qdrant '{}' creata automaticamente ({} dim, Cosine)",
                    collectionName, embeddingDimension);

        } catch (Exception e) {
            log.error("❌ Errore nella verifica/creazione collection Qdrant '{}': {}",
                    collectionName, e.getMessage(), e);
        }
    }
}
