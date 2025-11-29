package com.example.rag.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configurazione Multi-LLM per supportare diversi provider:
 * - Gemini (Google AI)
 * - Ollama (modelli locali)
 * - OpenRouter (gateway multi-LLM)
 */
@Configuration
@Slf4j
public class LlmConfig {

    // Provider selection
    @Value("${llm.provider:gemini}")
    private String llmProvider;

    @Value("${llm.temperature:0.3}")
    private Double temperature;

    @Value("${llm.max-tokens:1024}")
    private Integer maxTokens;

    // Gemini settings
    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String geminiModel;

    // Ollama settings
    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:llama3.2}")
    private String ollamaModel;

    @Value("${ollama.timeout:120}")
    private Integer ollamaTimeout;

    // OpenRouter settings
    @Value("${openrouter.api-key:}")
    private String openRouterApiKey;

    @Value("${openrouter.model:anthropic/claude-3-haiku}")
    private String openRouterModel;

    @Value("${openrouter.app-name:RAG-System}")
    private String openRouterAppName;

    @Value("${openrouter.app-url:}")
    private String openRouterAppUrl;

    /**
     * Crea il ChatLanguageModel basato sul provider configurato
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("🤖 Configurazione LLM Provider: {}", llmProvider);

        return switch (llmProvider.toLowerCase()) {
            case "gemini" -> createGeminiModel();
            case "ollama" -> createOllamaModel();
            case "openrouter" -> createOpenRouterModel();
            default -> {
                log.warn("⚠️ Provider '{}' non riconosciuto, uso Gemini come default", llmProvider);
                yield createGeminiModel();
            }
        };
    }

    /**
     * Crea il modello Google Gemini
     */
    private ChatLanguageModel createGeminiModel() {
        if (geminiApiKey == null || geminiApiKey.isEmpty() || geminiApiKey.equals("your_api_key_here")) {
            throw new IllegalStateException(
                "❌ GEMINI_API_KEY non configurata. " +
                "Imposta la variabile d'ambiente GEMINI_API_KEY o configura gemini.api-key in application.yml. " +
                "Ottieni una API key gratuita su: https://aistudio.google.com/app/apikey"
            );
        }

        log.info("✅ Inizializzazione Google Gemini - Modello: {}", geminiModel);
        
        return GoogleAiGeminiChatModel.builder()
                .apiKey(geminiApiKey)
                .modelName(geminiModel)
                .temperature(temperature)
                .maxOutputTokens(maxTokens)
                .build();
    }

    /**
     * Crea il modello Ollama (locale)
     */
    private ChatLanguageModel createOllamaModel() {
        log.info("✅ Inizializzazione Ollama - URL: {}, Modello: {}", ollamaBaseUrl, ollamaModel);
        
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaModel)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(ollamaTimeout))
                .build();
    }

    /**
     * Crea il modello OpenRouter (compatibile OpenAI API)
     * OpenRouter è un gateway che permette di accedere a molti LLM diversi
     */
    private ChatLanguageModel createOpenRouterModel() {
        if (openRouterApiKey == null || openRouterApiKey.isEmpty() || openRouterApiKey.equals("your_openrouter_key_here")) {
            throw new IllegalStateException(
                "❌ OPENROUTER_API_KEY non configurata. " +
                "Imposta la variabile d'ambiente OPENROUTER_API_KEY o configura openrouter.api-key in application.yml. " +
                "Ottieni una API key su: https://openrouter.ai/keys"
            );
        }

        log.info("✅ Inizializzazione OpenRouter - Modello: {}", openRouterModel);

        // OpenRouter usa un'API compatibile con OpenAI
        var builder = OpenAiChatModel.builder()
                .baseUrl("https://openrouter.ai/api/v1")
                .apiKey(openRouterApiKey)
                .modelName(openRouterModel)
                .temperature(temperature)
                .maxTokens(maxTokens);

        // Headers custom per OpenRouter (opzionali ma consigliati)
        // Questi header aiutano OpenRouter a tracciare le richieste e ottimizzare il routing
        if (openRouterAppName != null && !openRouterAppName.isEmpty()) {
            builder.customHeaders(java.util.Map.of(
                "HTTP-Referer", openRouterAppUrl != null && !openRouterAppUrl.isEmpty() 
                    ? openRouterAppUrl 
                    : "http://localhost:8092",
                "X-Title", openRouterAppName
            ));
        }

        return builder.build();
    }

    /**
     * Embedding Model LOCALE e GRATUITO
     * Non richiede API key, gira completamente offline
     * Genera vettori di 384 dimensioni
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("✅ Inizializzazione Embedding Model locale (AllMiniLmL6V2)");
        return new AllMiniLmL6V2EmbeddingModel();
    }
}
