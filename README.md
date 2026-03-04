# Sistema RAG con Qdrant e Multi-LLM Support

Sistema completo di **Retrieval-Augmented Generation (RAG)** per interrogare documenti usando:
- 🗄️ **Qdrant** - Vector database per gli embeddings
- 🤖 **Multi-LLM Support** - Google Gemini, Ollama (locale), OpenRouter
- � **Embedding Configurabile** - AllMiniLmL6V2 (locale) oppure Ollama (multilingue/italiano)
- 📄 **Apache Tika** - Estrazione testo da PDF, Word, Excel, ecc.
- ⚡ **LangChain4j** - Orchestrazione RAG
- 🐫 **Apache Camel** - Polling automatico directory
- 🍃 **Spring Boot 3.2.0** - Framework

## 🎯 Cosa Fa

1. **Upload Documenti**: Carica PDF, Word, Excel, PowerPoint, TXT, HTML via API REST
2. **Auto-Polling Directory**: Monitora automaticamente una directory e processa i nuovi file
3. **Indicizzazione**: Estrae il testo, lo divide in chunks, genera embeddings e salva in Qdrant
4. **Query Intelligenti**: Fai domande sui documenti e ricevi risposte contestualizzate con score di rilevanza

## 🔤 Embedding Model (Configurabile)

Il modello di embedding è **separato** dal provider LLM e si configura con `EMBEDDING_PROVIDER`:

| Provider | Modello | Dimensioni | Caratteristiche |
|----------|---------|-----------|-----------------|
| `local` (default) | AllMiniLmL6V2 | 384 | Offline, gratuito, ottimizzato per inglese |
| `ollama` | nomic-embed-text | 768 | Multilingue, ottimo italiano, consigliato |
| `ollama` | bge-m3 | 1024 | Migliore qualità multilingue, più lento |
| `ollama` | mxbai-embed-large | 1024 | Ottimo per europeo/inglese |

> ⚠️ **Importante**: ogni modello ha dimensioni vettore diverse. Se cambi modello, devi svuotare la collection Qdrant e re-indicizzare tutti i documenti.

### Configurazione Embedding

```bash
# Locale (default, inglese)
export EMBEDDING_PROVIDER=local

# Ollama multilingue (consigliato per doc italiani)
export EMBEDDING_PROVIDER=ollama
export EMBEDDING_OLLAMA_MODEL=nomic-embed-text  # 768 dim
export EMBEDDING_DIMENSION=768

# Installazione modello su Ollama:
docker exec ollama ollama pull nomic-embed-text
# oppure per qualità superiore:
docker exec ollama ollama pull bge-m3
export EMBEDDING_OLLAMA_MODEL=bge-m3
export EMBEDDING_DIMENSION=1024
```

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
# Con Docker (usa docker-compose in ollama/)
cd ollama && docker compose up -d

# Scarica il modello LLM
docker exec ollama ollama pull llama3.2:1b   # leggero, veloce
docker exec ollama ollama pull llama3.2       # 3B, bilanciato

# Scarica il modello embedding multilingue (consigliato per italiano)
docker exec ollama ollama pull nomic-embed-text  # 768 dim
# oppure alta qualità:
docker exec ollama ollama pull bge-m3            # 1024 dim

# Configura l'applicazione (usa il profilo ollama che imposta già tutto)
mvn spring-boot:run -Dspring-boot.run.profiles=ollama
```

Il profilo `ollama` preimposta automaticamente:
- `llm.provider=ollama` + `ollama.model=llama3.2:1b`
- `embedding.provider=ollama` + `embedding.ollama.model=nomic-embed-text` (768 dim)
- `embedding.dimension=768`
- `chunk-size=300`, `top-k=10`, `min-score=0.40` (ottimizzati per modelli piccoli)

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

### 1. Configura il Provider LLM e Embedding

```bash
# ===== Opzione A: Gemini + embedding locale (inglese) =====
export LLM_PROVIDER=gemini
export GEMINI_API_KEY=la_tua_api_key
# embedding local è il default (AllMiniLmL6V2, 384 dim)

# ===== Opzione B: Ollama + embedding multilingue (italiano) ===== (CONSIGLIATO)
# usa direttamente il profilo Spring: -Dspring-boot.run.profiles=ollama
# installa prima i modelli:
docker exec ollama ollama pull llama3.2:1b
docker exec ollama ollama pull nomic-embed-text

# ===== Opzione C: Gemini + embedding Ollama multilingue =====
export LLM_PROVIDER=gemini
export GEMINI_API_KEY=la_tua_api_key
export EMBEDDING_PROVIDER=ollama
export EMBEDDING_OLLAMA_MODEL=nomic-embed-text
export EMBEDDING_DIMENSION=768

# ===== Opzione D: OpenRouter =====
export LLM_PROVIDER=openrouter
export OPENROUTER_API_KEY=la_tua_api_key
export OPENROUTER_MODEL=anthropic/claude-3-haiku
```

### 2. Avvia il Sistema

```bash
chmod +x start.sh
./start.sh
```

Oppure con profilo Ollama (tutto locale, nessuna API key):

```bash
# 1. Avvia Qdrant e Ollama
docker compose up -d
cd ollama && docker compose up -d && cd ..

# 2. Avvia con profilo ollama
mvn spring-boot:run -Dspring-boot.run.profiles=ollama
```

Oppure manualmente con Gemini:

```bash
# 1. Avvia Qdrant
docker compose up -d

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
      "score": 0.776,
      "filename": "documento.pdf",
      "chunk_index": 3
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

# ============ EMBEDDING MODEL ============
embedding:
  # local = AllMiniLmL6V2 (offline, 384 dim, ottimizzato inglese)
  # ollama = modello Ollama multilingue (supporto italiano)
  provider: ${EMBEDDING_PROVIDER:local}
  ollama:
    model: ${EMBEDDING_OLLAMA_MODEL:nomic-embed-text}
    timeout: ${EMBEDDING_OLLAMA_TIMEOUT:60}
  # DEVE corrispondere al modello scelto: local=384, nomic-embed-text=768, bge-m3=1024
  dimension: ${EMBEDDING_DIMENSION:384}

# ============ RAG SETTINGS ============
rag:
  top-k: 20           # Candidati iniziali da recuperare da Qdrant
  min-score: 0.45     # Score minimo (cosine similarity). Chunks sotto soglia scartati
  max-chunks-per-doc: 4  # Limite chunks per singolo documento (evita monopolio)
  chunk-size: 500     # Dimensione chunks in caratteri (≥500 consigliato)
  chunk-overlap: 100  # Overlap tra chunks consecutivi

# ============ LLM CONFIGURATION ============
llm:
  # Provider: gemini | ollama | openrouter
  provider: ${LLM_PROVIDER:gemini}
  temperature: 0.3
  max-tokens: 2048

# Google Gemini
gemini:
  api-key: ${GEMINI_API_KEY:}
  model: gemini-2.5-flash

# Ollama (Local)
ollama:
  base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
  model: ${OLLAMA_MODEL:llama3.2:1b}
  timeout: 300

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

**top-k / min-score / max-chunks-per-doc:**
- `top-k`: candidati totali recuperati da Qdrant (poi filtrati). Aumentare per più copertura
- `min-score`: soglia cosine similarity (0.0-1.0). Valori consigliati:
  - `0.40`: permissiva (più contesto, possibile rumore)
  - `0.45`: bilanciata ✅ (default)
  - `0.60`: restrittiva (solo match molto pertinenti)
- `max-chunks-per-doc`: evita che un singolo file monopolizzi il contesto. Default: 4

**chunk-size:**
- `300`: chunk piccoli, match precisi (usato nel profilo Ollama per modelli con context window ridotto)
- `500`: bilanciato ✅ (default)
- `800+`: più contesto per chunk, meno chunk totali

> ⚠️ Dopo aver modificato `chunk-size` o `embedding.dimension` è necessario **svuotare Qdrant e re-indicizzare** tutti i documenti.

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
┌─────────────────────────────────┐
│  Text Splitter                  │  ← Divide in chunks
│  chunk-size=500, overlap=100    │     + chunk_index metadata
└──────┬──────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────┐
│ Embedding Model (configurabile)          │
│  local  → AllMiniLmL6V2   (384 dim)      │
│  ollama → nomic-embed-text (768 dim) ✅  │
│  ollama → bge-m3          (1024 dim)     │
└──────┬───────────────────────────────────┘
       │
       ▼
┌─────────────────┐
│    Qdrant       │  ← Salva vettori (auto-create collection)
└─────────────────┘


Query Flow:
┌─────────────┐
│  Domanda    │
└──────┬──────┘
       │
       ▼
┌─────────────────┐
│ Embedding Model │  ← Vettorizza domanda (stesso modello dell'ingestion)
└──────┬──────────┘
       │
       ▼
┌──────────────────────────────────┐
│ Qdrant Search (top-k=20)         │  ← Cerca simili
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────────────────────────┐
│ Filtro min-score + per-doc limit │  ← Scarta chunk irrilevanti
│  min-score=0.45, max/doc=4       │     e bilancia le fonti
└──────┬───────────────────────────┘
       │
       ▼
┌─────────────────┐
│ Build Prompt    │  ← Combina chunks con fonte + score + chunk#
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│   LLM Provider  │  ← Gemini / Ollama / OpenRouter
│  max-tokens=2048│
└──────┬──────────┘
       │
       ▼
┌──────────────────────────┐
│   Risposta + Sources     │
│   (score + chunk_index)  │
└──────────────────────────┘
```

## 🐛 Troubleshooting

### Qdrant connection refused
```bash
docker ps | grep qdrant
docker compose up -d
docker compose logs qdrant
```

### Invalid API key
```bash
echo $GEMINI_API_KEY
# oppure
echo $OPENROUTER_API_KEY
```

### Ollama non risponde
```bash
docker exec ollama ollama list              # Verifica modelli installati
docker exec ollama ollama pull llama3.2:1b  # Scarica modello LLM
docker exec ollama ollama pull nomic-embed-text  # Scarica modello embedding
```

### Vector dimension error (CRITICAL)
```
Wrong input: Vector dimension error: expected dim: 384, got 768
```
Il modello embedding è cambiato rispetto a quando è stata creata la collection Qdrant.  
**Soluzione**: svuota la collection e re-indicizza tutti i documenti:
```bash
# Elimina la collection esistente
curl -X DELETE http://localhost:6333/collections/documenti

# La nuova collection viene ricreata automaticamente al prossimo avvio
# con la dimensione configurata in embedding.dimension
```
Poi ri-carica tutti i documenti.

### File too large
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 200MB
      max-request-size: 200MB
```

### Risposte vuote / "Non trovo informazioni"
- Abbassa `rag.min-score` (es. da 0.45 a 0.35)
- Verifica che il documento sia stato processato: `GET /api/documents/status/{filename}`
- Verifica che embedding provider e dimensione coincidano tra ingestione e query
- Con modelli Ollama piccoli (1b): abbassa `rag.chunk-size` a 300 e `rag.top-k` a 10

### Errore compilazione
```bash
mvn clean package -DskipTests -U
```

## 📈 Performance

| Componente | Metrica | Note |
|------------|---------|------|
| **AllMiniLmL6V2** | ~1000 chunks/sec | Locale offline, 384 dim, ottimizzato inglese |
| **nomic-embed-text** (Ollama) | ~50-200 chunks/sec | 768 dim, multilingue, supporto italiano |
| **bge-m3** (Ollama) | ~20-100 chunks/sec | 1024 dim, massima qualità multilingue |
| **Gemini** | ~1-2 sec latenza | Gratuito fino a 1500 req/giorno |
| **Ollama llama3.2:1b** | ~2-5 sec | 100% locale, nessuna API key |
| **Ollama llama3.2 (3B)** | ~5-15 sec | Più preciso di 1b |
| **OpenRouter** | ~1-3 sec latenza | Pay-per-use |
| **Qdrant** | <100ms ricerca | 100k+ vettori |

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

- Embeddings generati **localmente** (AllMiniLmL6V2) oppure via **Ollama locale** (nessuna API esterna)
- Solo le query LLM usano API esterne (o Ollama locale)
- La collection Qdrant viene **creata automaticamente** all'avvio con la dimensione corretta
- File temporanei cancellati dopo il processing
- Ogni chunk contiene i metadata: `filename`, `upload_timestamp`, `chunk_index`, `chunk_total`

## 🔄 Modifiche Recenti

### RAG Quality Improvements
- **Score threshold** (`min-score=0.45`): i chunk con cosine similarity < soglia vengono scartati
- **Per-document limit** (`max-chunks-per-doc=4`): evita che un singolo file monopolizzi il contesto
- **Chunk size aumentato** da 300 a 500 caratteri (overlap 50→100): migliore coerenza semantica
- **max-tokens aumentato** da 1024 a 2048: evita troncamenti con contesti lunghi
- **top-k aumentato** a 20 (candidati iniziali, poi filtrati per score)
- **Prompt migliorato**: istruzioni per unire info da più fonti, gestione info contrastanti
- **Metadata chunk**: `chunk_index` e `chunk_total` su ogni segmento per tracciabilità
- **listIndexedDocuments**: usa Qdrant Scroll API con paginazione (non più embedding fittizio)
- **Sources arricchite**: le fonti in risposta mostrano `score`, `chunk_index`, `filename`

### Embedding Multilingue
- **Embedding configurabile**: `EMBEDDING_PROVIDER=local|ollama` + `EMBEDDING_DIMENSION`
- **Ollama embedding**: supporto per `nomic-embed-text` (768d), `bge-m3` (1024d), `mxbai-embed-large`
- **Auto-creazione collection** Qdrant all'avvio con la dimensione corretta
- **Fix bug LangChain4j 0.35**: `CustomQdrantEmbeddingStore` usa lo score Qdrant server-side (evita crash cosine similarity con vettori vuoti)
- **Profilo Ollama**: preconfigurato con `nomic-embed-text` per documenti italiani

## 📜 Licenza

Progetto fornito "as-is" per scopi educativi e di sviluppo.

esempi pdf: https://www.profwaltergalli.it/biblioteca/libri-in-formato-pdf/
