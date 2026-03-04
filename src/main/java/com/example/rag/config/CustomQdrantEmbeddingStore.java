package com.example.rag.config;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.WithPayloadSelector;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper che risolve il bug di LangChain4j 0.35.0 in QdrantEmbeddingStore:
 * la versione stock ricalcola il cosine similarity usando il vettore salvato
 * nel risultato Qdrant, ma Qdrant per default non lo restituisce → dimensione=0 → crash.
 *
 * Questa implementazione:
 * - Delega add/addAll a QdrantEmbeddingStore (funziona correttamente)
 * - Override findRelevant: usa QdrantClient direttamente con payload completo
 *   e usa lo score già calcolato da Qdrant (CosineSimilarity server-side)
 */
@Slf4j
public class CustomQdrantEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final String TEXT_KEY = "text_segment"; // LangChain4j 0.35 usa "text_segment"

    // Campi che NON sono metadata ma sono strutturali del payload Qdrant/LangChain4j
    private static final java.util.Set<String> PAYLOAD_SYSTEM_KEYS =
            java.util.Set.of("text_segment", "index");

    private final QdrantEmbeddingStore delegate;
    private final QdrantClient qdrantClient;
    private final String collectionName;

    public CustomQdrantEmbeddingStore(QdrantEmbeddingStore delegate,
                                       QdrantClient qdrantClient,
                                       String collectionName) {
        this.delegate = delegate;
        this.qdrantClient = qdrantClient;
        this.collectionName = collectionName;
    }

    // --- Delega operazioni di write ---

    @Override
    public String add(Embedding embedding) {
        return delegate.add(embedding);
    }

    @Override
    public void add(String id, Embedding embedding) {
        delegate.add(id, embedding);
    }

    @Override
    public String add(Embedding embedding, TextSegment embedded) {
        return delegate.add(embedding, embedded);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        return delegate.addAll(embeddings);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        return delegate.addAll(embeddings, embedded);
    }

    // --- Override findRelevant: usa Qdrant score direttamente ---

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding,
                                                           int maxResults) {
        return findRelevant(referenceEmbedding, maxResults, 0.0);
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding,
                                                           int maxResults,
                                                           double minScore) {
        try {
            // Converti il vettore di query in formato Qdrant
            List<Float> queryVector = toFloatList(referenceEmbedding.vector());

            SearchPoints searchRequest = SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .addAllVector(queryVector)
                    .setLimit(maxResults)
                    .setScoreThreshold((float) minScore)
                    .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                    // Non chiediamo i vettori (inutili, risparmiamo banda)
                    .setWithVectors(Points.WithVectorsSelector.newBuilder()
                            .setEnable(false).build())
                    .build();

            List<ScoredPoint> results = qdrantClient.searchAsync(searchRequest).get();

            List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
            for (ScoredPoint point : results) {
                double score = point.getScore();
                String id = point.getId().hasUuid()
                        ? point.getId().getUuid()
                        : String.valueOf(point.getId().getNum());

                // Estrae text e metadata dal payload Qdrant
                TextSegment segment = extractTextSegment(point);

                // Usa lo score direttamente da Qdrant (no ricalcolo CosineSimilarity)
                // Nota: EmbeddingMatch non è costruibile con score diretto nella versione 0.35,
                // quindi lo simuliamo con un Embedding vuoto (non usato a valle)
                matches.add(new EmbeddingMatch<>(score, id, Embedding.from(new float[0]), segment));
            }

            return matches;

        } catch (Exception e) {
            log.error("❌ Errore durante la ricerca in Qdrant: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Estrae il TextSegment dal payload di un ScoredPoint Qdrant.
     *
     * LangChain4j 0.35 salva il payload con struttura FLAT:
     *   "text_segment" → testo del chunk
     *   "index"        → indice interno LangChain4j (ignorato)
     *   tutti gli altri campi → metadata diretti (filename, chunk_index, upload_timestamp, ecc.)
     */
    private TextSegment extractTextSegment(ScoredPoint point) {
        var payload = point.getPayloadMap();

        // Estrai testo (chiave "text_segment" in LangChain4j 0.35)
        String text = "";
        var textVal = payload.get(TEXT_KEY);
        if (textVal != null && textVal.hasStringValue()) {
            text = textVal.getStringValue();
        }

        // Estrai metadata da tutti i campi del payload eccetto quelli di sistema
        dev.langchain4j.data.document.Metadata metadata = new dev.langchain4j.data.document.Metadata();
        payload.forEach((key, value) -> {
            if (PAYLOAD_SYSTEM_KEYS.contains(key)) return; // salta campi di struttura
            if (value.hasStringValue()) {
                metadata.put(key, value.getStringValue());
            } else if (value.hasIntegerValue()) {
                long lv = value.getIntegerValue();
                if (lv >= Integer.MIN_VALUE && lv <= Integer.MAX_VALUE) {
                    metadata.put(key, (int) lv);
                } else {
                    metadata.put(key, lv);
                }
            } else if (value.hasDoubleValue()) {
                metadata.put(key, value.getDoubleValue());
            }
        });

        return TextSegment.from(text, metadata);
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float f : vector) {
            list.add(f);
        }
        return list;
    }
}
