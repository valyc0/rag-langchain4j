# Come Funziona il Sistema RAG

Guida completa al funzionamento interno, ai modelli usati e a come cambiarli.

---

## 1. I Due Modelli: Embedding vs LLM

Il sistema usa **due modelli distinti** che svolgono ruoli completamente separati.

```
┌─────────────────────────────────────────────────────────────┐
│  EMBEDDING MODEL                                            │
│  Trasforma testo in vettori numerici (es. 768 numeri)      │
│  Non "capisce", non genera: converte soltanto              │
│  Usato SIA durante l'upload SIA durante la query           │
│                                                             │
│  config: embedding.provider / embedding.ollama.model       │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  LLM (Language Model)                                       │
│  Legge il contesto estratto da Qdrant e scrive la risposta │
│  Usato SOLO durante la query (fase finale)                  │
│                                                             │
│  config: llm.provider / ollama.model / gemini.model        │
└─────────────────────────────────────────────────────────────┘
```

Puoi combinarli liberamente: es. **Gemini** per le risposte + **nomic-embed-text** per l'embedding.  
L'unico vincolo: il modello embedding **deve essere lo stesso** usato durante l'upload dei documenti, perché la domanda deve cadere nello stesso spazio vettoriale dei chunk salvati in Qdrant.

---

## 2. Il Flusso Completo

### 2a. Upload di un documento

```
PDF / DOCX / TXT
      │
      ▼
Apache Tika           ← estrae il testo grezzo
      │
      ▼
Text Splitter         ← divide in chunk da 500 char (overlap 100)
      │                  aggiunge metadata: filename, chunk_index, chunk_total
      ▼
Embedding Model       ← ogni chunk → vettore float[] (es. 768 dim)
      │                  inviato a Ollama in batch da 10 per evitare timeout
      ▼
Qdrant                ← salva (vettore + testo + metadata)
```

### 2b. Query dell'utente

```
"Chi è Virgilio nell'Inferno?"
      │
      ▼
Embedding Model       ← stessa domanda → vettore float[]
      │
      ▼
Qdrant findRelevant   ← ricerca i 20 chunk più vicini (cosine similarity)
      │
      ▼
Filtro min-score      ← scarta chunk con score < 0.45
      │
      ▼
Filtro per-doc        ← massimo 4 chunk per documento (evita che un file
      │                  monopolizzi tutto il contesto)
      ▼
Build Prompt          ← assembla: istruzioni sistema + chunk numerati
      │                  con [Fonte 1: file.pdf #3 | rilevanza: 0.77]
      ▼
LLM                   ← legge il prompt e genera la risposta in testo
      │
      ▼
Risposta + Sources    ← testo risposta + lista fonti con score e chunk_index
```

---

## 3. Embedding Model — Opzioni e Default

### Default (senza profilo specifico)

| Parametro | Valore |
|-----------|--------|
| `embedding.provider` | `local` |
| Modello | `AllMiniLmL6V2` |
| Dimensioni vettore | 384 |
| Dove gira | in-process, nessun server esterno |
| Supporto italiano | parziale (ottimizzato per inglese) |
| Velocità | ~1000 chunk/sec |

### Con profilo ollama

| Parametro | Valore |
|-----------|--------|
| `embedding.provider` | `ollama` |
| Modello | `nomic-embed-text` |
| Dimensioni vettore | 768 |
| Dove gira | server Ollama (Docker, porta 11434) |
| Supporto italiano | ottimo |
| Velocità | ~50-200 chunk/sec |

### Tutti i modelli embedding disponibili

| Modello | Dim | Multilingue | Velocità | Quando usarlo |
|---------|-----|-------------|----------|---------------|
| `AllMiniLmL6V2` (local) | 384 | ⚠️ inglese | velocissimo | documenti in inglese, offline |
| `nomic-embed-text` | 768 | ✅ ottimo | medio | **consigliato per italiano** |
| `bge-m3` | 1024 | ✅ eccellente | lento | massima qualità multilingue |
| `mxbai-embed-large` | 1024 | ✅ buono | lento | alternativa a bge-m3 |

> ⚠️ **Regola critica**: se cambi modello embedding, la dimensione del vettore cambia.  
> Devi **eliminare la collection Qdrant e re-indicizzare** tutti i documenti.  
> ```bash
> curl -X DELETE http://localhost:6333/collections/documenti
> # al prossimo avvio Spring la ricrea con la dimensione corretta
> ```

### Come cambiare l'embedding model

**Opzione A — variabili d'ambiente:**
```bash
export EMBEDDING_PROVIDER=ollama
export EMBEDDING_OLLAMA_MODEL=bge-m3
export EMBEDDING_DIMENSION=1024
mvn spring-boot:run
```

**Opzione B — application.yml:**
```yaml
embedding:
  provider: ollama
  ollama:
    model: bge-m3
    timeout: 120
  dimension: 1024
  batch-size: 10
```

**Opzione C — profilo ollama (già preconfigurato):**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=ollama
# usa automaticamente nomic-embed-text 768d
```

---

## 4. LLM (Generativo) — Opzioni e Default

### Default (senza profilo specifico)

| Parametro | Valore |
|-----------|--------|
| `llm.provider` | `gemini` |
| Modello | `gemini-2.5-flash` |
| API Key necessaria | sì (`GEMINI_API_KEY`) |
| Costo | gratuito fino a 1500 req/giorno |
| Latenza | ~1-2 sec |
| Qualità italiano | eccellente |

### Con profilo ollama

| Parametro | Valore |
|-----------|--------|
| `llm.provider` | `ollama` |
| Modello | `llama3.2:1b` |
| API Key necessaria | no |
| Costo | gratuito, 100% locale |
| Latenza | ~2-5 sec (CPU), ~0.5s (GPU) |
| Context window | 2048 token |

### Tutti i provider LLM

#### Google Gemini

```yaml
llm:
  provider: gemini
  temperature: 0.3
  max-tokens: 2048

gemini:
  api-key: ${GEMINI_API_KEY}
  model: gemini-2.5-flash    # oppure: gemini-1.5-pro, gemini-1.5-flash
```

Ottieni la chiave gratuita su [Google AI Studio](https://aistudio.google.com/app/apikey).

| Modello | Note |
|---------|------|
| `gemini-2.5-flash` | Più recente, veloce (**default**) |
| `gemini-1.5-pro` | Più capace, sempre gratuito |
| `gemini-1.5-flash` | Stabile, veloce |

#### Ollama (locale, nessuna API key)

```yaml
llm:
  provider: ollama
  temperature: 0.5
  max-tokens: 512            # ridotto per modelli piccoli

ollama:
  base-url: http://localhost:11434
  model: llama3.2:1b         # oppure: llama3.2, mistral, mixtral...
  timeout: 300
```

| Modello | Param | Context | Note |
|---------|-------|---------|------|
| `llama3.2:1b` | 1B | 2048 | velocissimo, leggero (**default profilo ollama**) |
| `llama3.2` | 3B | 4096 | buon compromesso |
| `llama3.1` | 8B | 8192 | più capace, più lento |
| `mistral` | 7B | 8192 | ottimo multilingue |
| `mixtral` | 8×7B | 32768 | MoE, molto capace |
| `phi3` | 3.8B | 4096 | compatto, ottimo per italiano |
| `qwen2` | 7B | 8192 | buon supporto italiano |

Per installare un modello:
```bash
docker exec ollama ollama pull llama3.2
docker exec ollama ollama list    # verifica modelli installati
```

#### OpenRouter (gateway multi-LLM, pay-per-use)

```yaml
llm:
  provider: openrouter
  temperature: 0.3
  max-tokens: 2048

openrouter:
  api-key: ${OPENROUTER_API_KEY}
  model: anthropic/claude-3-haiku   # oppure: openai/gpt-4-turbo, ecc.
```

Modelli gratuiti disponibili su [openrouter.ai/models](https://openrouter.ai/models) (filtra per "free").

### Come cambiare il provider LLM

**Variabili d'ambiente (senza modificare YAML):**
```bash
# Gemini
export LLM_PROVIDER=gemini
export GEMINI_API_KEY=la_tua_chiave

# Ollama locale
export LLM_PROVIDER=ollama
export OLLAMA_MODEL=mistral         # cambia solo il modello

# OpenRouter
export LLM_PROVIDER=openrouter
export OPENROUTER_API_KEY=la_tua_chiave
export OPENROUTER_MODEL=anthropic/claude-3-sonnet
```

---

## 5. Combinazioni Consigliate

| Scenario | Embedding | LLM | Note |
|----------|-----------|-----|------|
| **Tutto locale (italiano)** | `nomic-embed-text` 768d | `llama3.2:1b` | usa il profilo `ollama` |
| **Italiano alta qualità** | `nomic-embed-text` 768d | `gemini-2.5-flash` | embedding locale, LLM cloud |
| **Inglese, no costi** | `AllMiniLmL6V2` 384d | `gemini-2.5-flash` | default, nessun setup Ollama |
| **Massima qualità** | `bge-m3` 1024d | `llama3.1` (8B) o GPT-4 | richiede GPU per Ollama |
| **Documenti tecnici IT** | `nomic-embed-text` 768d | `mistral` 7B | ottimo per documentazione |

### Come avviare le combinazioni

```bash
# Tutto locale (profilo ollama - tutto preconfigurato)
mvn spring-boot:run -Dspring-boot.run.profiles=ollama

# Embedding italiano + Gemini (senza profilo)
export EMBEDDING_PROVIDER=ollama
export EMBEDDING_OLLAMA_MODEL=nomic-embed-text
export EMBEDDING_DIMENSION=768
export LLM_PROVIDER=gemini
export GEMINI_API_KEY=la_tua_chiave
mvn spring-boot:run

# Cambia solo il modello LLM Ollama (mantieni tutto il resto del profilo)
OLLAMA_MODEL=mistral mvn spring-boot:run -Dspring-boot.run.profiles=ollama
```

---

## 6. Parametri RAG e Come Regolarli

Questi parametri controllano **quanti e quali chunk** passare all'LLM.

```yaml
rag:
  top-k: 20           # [default] / 10 [profilo ollama]
  min-score: 0.45     # [default] / 0.40 [profilo ollama]
  max-chunks-per-doc: 4  # [default] / 3 [profilo ollama]
  chunk-size: 500     # [default] / 300 [profilo ollama]
  chunk-overlap: 100  # [default] / 60 [profilo ollama]
```

### top-k
Candidati iniziali recuperati da Qdrant (poi filtrati).
- **Aumenta** se le risposte mancano contesto pertinente
- **Diminuisci** se l'LLM è lento o consumi troppi token

### min-score (0.0 – 1.0)
Soglia cosine similarity. Chunk sotto questa soglia vengono scartati.

| Valore | Effetto | Quando usarlo |
|--------|---------|---------------|
| `0.30` | molto permissivo | domande vague, documenti eterogenei |
| `0.40` | permissivo | profilo ollama, modelli piccoli |
| `0.45` | bilanciato (**default**) | uso generale |
| `0.60` | restrittivo | solo match molto precisi |

### max-chunks-per-doc
Evita che un singolo documento occupi tutto il contesto.  
Con 3 documenti e `max-chunks-per-doc=4` l'LLM riceve al max 12 chunk totali bilanciati.

### chunk-size
Dimensione in caratteri di ogni chunk.
- **Piccoli (300)**: match più precisi, più chunk totali — buono per LLM con context window piccolo
- **Grandi (500-800)**: più contesto per chunk, migliore coerenza semantica

### chunk-overlap
Quanti caratteri si sovrappongono tra chunk consecutivi. Evita di spezzare frasi a metà.

---

## 7. Il Prompt e il Ruolo dell'LLM

L'LLM riceve un prompt strutturato così:

```
Sei un assistente esperto che risponde a domande basandosi
ESCLUSIVAMENTE sulle informazioni fornite nel contesto.

ISTRUZIONI:
- Rispondi SOLO usando le informazioni nel contesto
- Se l'informazione non è nel contesto, dillo esplicitamente
- Unisci informazioni da più fonti quando disponibili
- Cita la fonte quando rilevante

CONTESTO:
[Fonte 1: 02 Inferno.pdf #116 | rilevanza: 0.77]
Il poeta è minacciato da tre animali che sono il
simbolo di tre vizi...

[Fonte 2: 02 Inferno.pdf #293 | rilevanza: 0.76]
Non siamo nati per vivere come gli animali privi di
ragione...

DOMANDA: Descrivi i tre animali del primo canto

RISPOSTA:
```

L'LLM **non ha accesso diretto ai documenti**: vede solo i chunk selezionati da Qdrant.  
Questo è il principio fondamentale del RAG: l'LLM *ragiona* sul contesto, non *memorizza*.

---

## 8. Auto-Creazione Collection Qdrant

All'avvio, Spring verifica se la collection `documenti` esiste in Qdrant.  
Se non esiste, la crea automaticamente con la dimensione configurata in `embedding.dimension`.

```
Avvio app → ApplicationReadyEvent → QdrantConfig.initCollection()
  │
  ├── collection esiste? → log "già esistente (dim: 768)" → OK
  └── non esiste?        → crea con VectorParams(size=768, distance=Cosine)
```

Se cambi modello embedding (es. da 384 a 768 dim), devi eliminare manualmente la vecchia collection:
```bash
curl -X DELETE http://localhost:6333/collections/documenti
# al prossimo avvio viene ricreata con la nuova dimensione
```

---

## 9. Batch Embedding (per documenti grandi)

Il parametro `embedding.batch-size` controlla quanti chunk vengono inviati a Ollama per singola richiesta HTTP.

**Perché serve**: un PDF grande (es. L'Inferno di Dante = 2637 chunk) invierebbe una richiesta HTTP enorme, causando un timeout.

```
Chunk totali: 2637  /  batch-size: 10  =  264 richieste da 10 chunk ciascuna
```

| Config | Valore default | Valore profilo ollama |
|--------|---------------|----------------------|
| `embedding.batch-size` | 20 | 10 |
| `embedding.ollama.timeout` | 60s | 120s |

Per documenti molto grandi o sistemi lenti, abbassa ulteriormente:
```yaml
embedding:
  batch-size: 5
  ollama:
    timeout: 180
```

---

## 10. Architettura dei File

```
src/main/java/com/example/rag/
│
├── config/
│   ├── LlmConfig.java              ← crea i bean EmbeddingModel e ChatLanguageModel
│   │                                  switch su llm.provider e embedding.provider
│   ├── QdrantConfig.java           ← crea EmbeddingStore, inizializza collection
│   └── CustomQdrantEmbeddingStore  ← fix bug LangChain4j 0.35 (usa score Qdrant
│                                      server-side, non ricalcola cosine localmente)
│
├── service/
│   ├── DocumentProcessingService   ← upload → tika → split → embed → qdrant
│   ├── RagQueryService             ← domanda → embed → search → filter → LLM
│   └── DocumentStatusService       ← traccia stato PROCESSING/READY/ERROR
│
├── controller/
│   ├── DocumentController          ← POST /api/documents/upload
│   │                                  GET  /api/documents/list
│   │                                  GET  /api/documents/status/{filename}
│   │                                  DELETE /api/documents/{filename}
│   └── QueryController             ← GET/POST /api/query
│
└── camel/
    └── FilePollingRoute            ← Apache Camel, monitora rag-input/
```

---

## 11. Riferimento Rapido

### Variabili d'ambiente principali

| Variabile | Default | Descrizione |
|-----------|---------|-------------|
| `LLM_PROVIDER` | `gemini` | Provider LLM: `gemini`, `ollama`, `openrouter` |
| `GEMINI_API_KEY` | — | API key Google AI Studio |
| `OLLAMA_MODEL` | `llama3.2:1b` | Modello Ollama per le risposte |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | URL server Ollama |
| `OPENROUTER_API_KEY` | — | API key OpenRouter |
| `OPENROUTER_MODEL` | `x-ai/grok-4.1-fast:free` | Modello OpenRouter |
| `EMBEDDING_PROVIDER` | `local` | Provider embedding: `local`, `ollama` |
| `EMBEDDING_OLLAMA_MODEL` | `nomic-embed-text` | Modello embedding Ollama |
| `EMBEDDING_DIMENSION` | `384` | Dimensione vettore (deve corrispondere al modello) |

### Profili Spring disponibili

| Profilo | Comando | Cosa configura |
|---------|---------|----------------|
| (nessuno / `gemini`) | `mvn spring-boot:run` | Gemini + AllMiniLmL6V2 locale |
| `ollama` | `mvn spring-boot:run -Dspring-boot.run.profiles=ollama` | llama3.2:1b + nomic-embed-text 768d |

### Porte

| Servizio | Porta |
|---------|-------|
| Backend RAG (API REST) | 8092 |
| Client Web (Vaadin UI) | 8093 |
| Qdrant HTTP | 6333 |
| Qdrant gRPC | 6334 |
| Ollama | 11434 |
