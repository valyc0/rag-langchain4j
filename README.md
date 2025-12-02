# Sistema RAG con Qdrant e Multi-LLM Support

Sistema completo di **Retrieval-Augmented Generation (RAG)** per interrogare documenti usando:
- 🗄️ **Qdrant** - Vector database per gli embeddings
- 🤖 **Multi-LLM Support** - Google Gemini, Ollama (locale), OpenRouter
- 📄 **Apache Tika** - Estrazione testo da PDF, Word, Excel, ecc.
- ⚡ **LangChain4j** - Orchestrazione RAG
- 🐫 **Apache Camel** - Polling automatico directory
- 🍃 **Spring Boot 3.2.0** - Framework

## 🎯 Cosa Fa

1. **Upload Documenti**: Carica PDF, Word, Excel, PowerPoint, TXT, HTML via API REST
2. **Auto-Polling Directory**: Monitora automaticamente una directory e processa i nuovi file
3. **Indicizzazione**: Estrae il testo, lo divide in chunks, genera embeddings e salva in Qdrant
4. **Query Intelligenti**: Fai domande sui documenti e ricevi risposte contestualizzate

## 🤖 Provider LLM Supportati

Il sistema supporta **3 provider LLM** configurabili:

| Provider | Descrizione | Costo | Requisiti |
|----------|-------------|-------|-----------|
| **Gemini** | Google AI, veloce e intelligente | GRATIS (1500 req/giorno) | API Key |
| **Ollama** | Modelli locali (Llama, Mistral, ecc.) | GRATIS | Installazione locale |
| **OpenRouter** | Gateway multi-LLM (GPT-4, Claude, ecc.) | Pay-per-use | API Key |

### 🔧 Configurazione Provider LLM

Imposta il provider tramite variabile d'ambiente `LLM_PROVIDER` o in `application.yml`:

```bash
# ========== GEMINI (default) ==========
export LLM_PROVIDER=gemini
export GEMINI_API_KEY=la_tua_api_key

# ========== OLLAMA (locale) ==========
export LLM_PROVIDER=ollama
export OLLAMA_MODEL=llama3.2
# opzionale: export OLLAMA_BASE_URL=http://localhost:11434

# ========== OPENROUTER ==========
export LLM_PROVIDER=openrouter
export OPENROUTER_API_KEY=la_tua_api_key
export OPENROUTER_MODEL=anthropic/claude-3-haiku
```

### 📊 Modelli Disponibili

#### Google Gemini
| Modello | Descrizione | Costo |
|---------|-------------|-------|
| `gemini-2.5-flash` | Più recente, veloce | GRATIS |
| `gemini-2.0-flash-exp` | Sperimentale | GRATIS |
| `gemini-1.5-flash` | Veloce, stabile | GRATIS |
| `gemini-1.5-pro` | Più capace | GRATIS |

#### Ollama (Modelli Locali)
| Modello | Dimensione | Uso consigliato |
|---------|------------|-----------------|
| `llama3.2` | 3B/11B | General purpose, veloce |
| `llama3.1` | 8B/70B | Potente, multilingua |
| `mistral` | 7B | Ottimo rapporto qualità/velocità |
| `mixtral` | 8x7B | MoE, molto capace |
| `codellama` | 7B/13B/34B | Codice e documentazione tecnica |
| `phi3` | 3.8B | Compatto, veloce |
| `qwen2` | 0.5B-72B | Multilingua, cinese/inglese |

#### OpenRouter
| Modello | Provider | Caratteristiche |
|---------|----------|-----------------|
| `openai/gpt-4-turbo` | OpenAI | Top quality, costoso |
| `openai/gpt-3.5-turbo` | OpenAI | Veloce, economico |
| `anthropic/claude-3-opus` | Anthropic | Massima qualità |
| `anthropic/claude-3-sonnet` | Anthropic | Bilanciato |
| `anthropic/claude-3-haiku` | Anthropic | Veloce, economico |
| `google/gemini-pro` | Google | Via OpenRouter |
| `meta-llama/llama-3-70b-instruct` | Meta | Open source, potente |
| `mistralai/mixtral-8x7b-instruct` | Mistral | MoE |

Lista completa: [OpenRouter Models](https://openrouter.ai/models)

### 🦙 Setup Ollama

```bash
# 1. Installa Ollama
curl -fsSL https://ollama.ai/install.sh | sh

# 2. Scarica un modello
ollama pull llama3.2

# 3. Verifica che funzioni
ollama run llama3.2 "Ciao!"

# 4. Configura l'applicazione
export LLM_PROVIDER=ollama
export OLLAMA_MODEL=llama3.2
```

### 🔑 Ottenere API Keys

**Gemini (Gratuito):**
1. Vai su [Google AI Studio](https://aistudio.google.com/app/apikey)
2. Login con account Google
3. "Create API Key"
4. Include: 1500 req/giorno, 1M token/minuto, nessuna carta di credito

**OpenRouter:**
1. Vai su [OpenRouter](https://openrouter.ai/keys)
2. Crea account
3. Genera API key
4. Ricarica credito

## 🆕 Auto-Polling Directory con Apache Camel

Il sistema monitora automaticamente una directory per nuovi file:

- 📂 Polling ogni 5 secondi (configurabile)
- 🔍 Filtra: PDF, DOC, DOCX, XLS, XLSX, PPT, PPTX, TXT, HTML, XML
- ⚡ Processing parallelo (3 file simultanei)
- ✅ Sposta file processati in directory separata
- ❌ Gestisce errori con error-directory

### Configurazione Polling

```yaml
file-polling:
  enabled: true
  input-directory: rag-input
  processed-directory: rag-processed
  error-directory: rag-errors
  file-pattern: .*\\.(pdf|doc|docx|xls|xlsx|ppt|pptx|txt|html|xml)$
  delay: 5000
  initial-delay: 1000
  max-concurrent: 3
```

### Uso del Polling

```bash
# Le directory vengono create automaticamente
# Copia i file da processare:
cp documento.pdf rag-input/

# Il sistema processa automaticamente!
# Logs:
# 📥 Nuovo file rilevato: documento.pdf
# 🔄 Inizio processamento file: documento.pdf
# ✅ File processato con successo
```

## 📋 Prerequisiti

- **Java 17+**
- **Maven 3.6+**
- **Docker & Docker Compose**
- **API Key** (Gemini o OpenRouter) oppure **Ollama** installato

## 🚀 Quick Start

### 1. Configura il Provider LLM

```bash
# Opzione A: Gemini (consigliato per iniziare)
export LLM_PROVIDER=gemini
export GEMINI_API_KEY=la_tua_api_key

# Opzione B: Ollama (nessuna API key necessaria)
export LLM_PROVIDER=ollama
ollama pull llama3.2

# Opzione C: OpenRouter (accesso a tutti i modelli)
export LLM_PROVIDER=openrouter
export OPENROUTER_API_KEY=la_tua_api_key
export OPENROUTER_MODEL=anthropic/claude-3-haiku
```

### 2. Avvia il Sistema

```bash
chmod +x start.sh
./start.sh
```

Oppure manualmente:

```bash
# 1. Avvia Qdrant
docker-compose up -d

# 2. Compila e avvia
mvn clean package
java -jar target/rag-system-1.0.0.jar
```

L'applicazione sarà disponibile su: **http://localhost:8092**

## 📡 API Endpoints

### Upload Documento

```bash
curl -F "file=@documento.pdf" http://localhost:8092/api/documents/upload
```

### Query (GET)

```bash
curl "http://localhost:8092/api/query?question=Di%20cosa%20parla%20il%20documento?"
```

### Query (POST)

```bash
curl -X POST http://localhost:8092/api/query \
  -H "Content-Type: application/json" \
  -d '{"question": "Quali sono i punti principali?"}'
```

**Risposta:**
```json
{
  "answer": "Il documento tratta principalmente di...",
  "sources": [
    {
      "text": "Testo del chunk rilevante...",
      "score": 0.89,
      "filename": "documento.pdf"
    }
  ],
  "question": "Di cosa parla il documento?",
  "chunks_used": 5
}
```

### Health Check

```bash
curl http://localhost:8092/api/documents/health
curl http://localhost:8092/api/query/health
```

## 📂 Formati Supportati

- ✅ PDF (.pdf)
- ✅ Microsoft Word (.doc, .docx)
- ✅ Microsoft Excel (.xls, .xlsx)
- ✅ Microsoft PowerPoint (.ppt, .pptx)
- ✅ Testo (.txt)
- ✅ HTML (.html)
- ✅ XML (.xml)

## ⚙️ Configurazione Completa

`src/main/resources/application.yml`:

```yaml
server:
  port: 8092

# Qdrant Vector Database
qdrant:
  host: localhost
  port: 6334
  collection-name: documenti

# RAG Settings
rag:
  top-k: 15          # Chunks da recuperare per query
  chunk-size: 300    # Dimensione chunks
  chunk-overlap: 50  # Overlap tra chunks

# ============ LLM CONFIGURATION ============
llm:
  # Provider: gemini | ollama | openrouter
  provider: ${LLM_PROVIDER:gemini}
  temperature: 0.3
  max-tokens: 1024

# Google Gemini
gemini:
  api-key: ${GEMINI_API_KEY:}
  model: gemini-2.5-flash

# Ollama (Local)
ollama:
  base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
  model: ${OLLAMA_MODEL:llama3.2}
  timeout: 120

# OpenRouter
openrouter:
  api-key: ${OPENROUTER_API_KEY:}
  model: ${OPENROUTER_MODEL:anthropic/claude-3-haiku}
  app-name: RAG-System

# File Polling
file-polling:
  enabled: true
  input-directory: rag-input
  processed-directory: rag-processed
  error-directory: rag-errors
  delay: 5000
  max-concurrent: 3
```

### 🎛️ Tuning Parametri

**Temperature:**
- `0.0-0.3`: Deterministico, preciso (FAQ, documentazione)
- `0.3-0.7`: Bilanciato (uso generale RAG)
- `0.7-1.0`: Creativo (rischio allucinazioni)

**Top-K:**
- `5-10`: Veloce, documenti semplici
- `10-20`: Più contesto, domande complesse
- `20+`: Massimo contesto, più lento

## 📊 Architettura

```
Upload Flow:
┌─────────────┐
│   Upload    │
│  PDF/Word   │
└──────┬──────┘
       │
       ▼
┌─────────────────┐
│ Apache Tika     │  ← Estrae testo
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│  Text Splitter  │  ← Divide in chunks
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│ AllMiniLmL6V2   │  ← Embeddings (LOCALE)
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│    Qdrant       │  ← Salva vettori
└─────────────────┘


Query Flow:
┌─────────────┐
│  Domanda    │
└──────┬──────┘
       │
       ▼
┌─────────────────┐
│ Embedding Model │  ← Vettorizza domanda
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│ Qdrant Search   │  ← Cerca simili
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│ Build Prompt    │  ← Combina chunks
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│   LLM Provider  │  ← Gemini/Ollama/
│                 │     OpenRouter
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│   Risposta +    │
│    Sources      │
└─────────────────┘
```

## 🐛 Troubleshooting

### Qdrant connection refused
```bash
docker ps | grep qdrant
docker-compose up -d
docker-compose logs qdrant
```

### Invalid API key
```bash
echo $GEMINI_API_KEY
# oppure
echo $OPENROUTER_API_KEY
```

### Ollama non risponde
```bash
ollama list           # Verifica modelli
ollama serve          # Avvia server
ollama pull llama3.2  # Scarica modello
```

### File too large
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 200MB
      max-request-size: 200MB
```

### Errore compilazione
```bash
mvn clean package -DskipTests -U
```

## 📈 Performance

| Componente | Metrica | Note |
|------------|---------|------|
| **Embedding** | ~1000 chunks/sec | Locale, gratuito |
| **Gemini** | ~1-2 sec latenza | Gratuito |
| **Ollama** | Dipende da hardware | Gratuito |
| **OpenRouter** | ~1-3 sec latenza | Pay-per-use |
| **Qdrant** | <100ms ricerca | 100k vettori |

## 🗄️ Gestione Qdrant

**Dashboard Web:** http://localhost:6333/dashboard

```bash
# Log
docker-compose logs qdrant

# Riavvia
docker-compose restart qdrant

# Stop
docker-compose down

# Reset completo
docker-compose down -v
rm -rf qdrant_storage/
```

## 🔐 Sicurezza

- ✅ API keys via variabili d'ambiente
- ✅ Nessuna key hardcoded
- ✅ Validazione file input
- ⚠️ In produzione: aggiungere autenticazione (OAuth2, JWT)

## 📝 Note

- Embeddings generati **localmente** (gratuito)
- Solo le query LLM usano API esterne (o Ollama locale)
- Dati Qdrant persistenti
- File temporanei cancellati dopo processing

## 📜 Licenza

Progetto fornito "as-is" per scopi educativi e di sviluppo.

esempi pdf: https://www.profwaltergalli.it/biblioteca/libri-in-formato-pdf/
