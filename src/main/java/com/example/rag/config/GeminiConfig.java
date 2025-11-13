package com.example.rag.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configurazione per Google Gemini 2.0 Flash e Embedding Model
 */
@Configuration
public class GeminiConfig {

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.0-flash-exp}")
    private String modelName;

    @Value("${gemini.temperature:0.7}")
    private Double temperature;

    @Value("${gemini.max-tokens:2048}")
    private Integer maxTokens;

    /**
     * Google Gemini Chat Model con API diretta
     * Supporta: gemini-2.0-flash-exp, gemini-1.5-flash, gemini-1.5-pro
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            throw new IllegalStateException(
                "GEMINI_API_KEY non configurata. " +
                "Imposta la variabile d'ambiente o configura gemini.api-key in application.yml"
            );
        }

        return GoogleAiGeminiChatModel.builder()
                .apiKey(geminiApiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxOutputTokens(maxTokens)
                .build();
    }

    /**
     * Embedding Model LOCALE e GRATUITO
     * Non richiede API key, gira completamente offline
     * Genera vettori di 384 dimensioni
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }
}
