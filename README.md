# Sistema RAG con Qdrant e Google Gemini 2.0 Flash

Sistema completo di **Retrieval-Augmented Generation (RAG)** per interrogare documenti usando:
- 🗄️ **Qdrant** - Vector database per gli embeddings
- 🤖 **Google Gemini 2.0 Flash** - LLM per generare risposte
- 📄 **Apache Tika** - Estrazione testo da PDF, Word, Excel, ecc.
- ⚡ **LangChain4j** - Orchestrazione RAG
- 🐫 **Apache Camel** - Polling automatico directory
- 🍃 **Spring Boot 3.2.0** - Framework

## 🎯 Cosa Fa

1. **Upload Documenti**: Carica PDF, Word, Excel, PowerPoint, TXT, HTML via API REST
2. **Auto-Polling Directory**: Monitora automaticamente una directory e processa i nuovi file
3. **Indicizzazione**: Estrae il testo, lo divide in chunks, genera embeddings e salva in Qdrant
4. **Query Intelligenti**: Fai domande sui documenti e ricevi risposte contestualizzate da Gemini

## 🆕 Novità: Auto-Polling Directory con Apache Camel

Il sistema ora include un **meccanismo di polling automatico** che monitora una directory configurabile:

- 📂 Monitora automaticamente una directory ogni 5 secondi (configurabile)
- 🔍 Filtra solo file supportati (PDF, DOC, DOCX, XLS, XLSX, PPT, PPTX, TXT, HTML, XML)
- ⚡ Processa fino a 3 file in parallelo (configurabile)
- ✅ Sposta i file processati in una directory separata
- ❌ Gestisce errori spostando i file problematici in error-directory
- 🎛️ Completamente configurabile via `application.yml`

### Configurazione Auto-Polling

Modifica `src/main/resources/application.yml`:

```yaml
file-polling:
  # Abilita/disabilita il polling automatico
  enabled: true
  
  # Directory da monitorare per nuovi file
  input-directory: ${HOME}/rag-input
  
  # Directory dove spostare i file processati
  processed-directory: ${HOME}/rag-processed
  
  # Directory per i file che hanno dato errore
  error-directory: ${HOME}/rag-errors
  
  # Pattern dei file da processare (regex Java)
  file-pattern: .*\\.(pdf|doc|docx|xls|xlsx|ppt|pptx|txt|html|xml)$
  
  # Frequenza di polling in millisecondi (5000ms = 5 secondi)
  delay: 5000
  
  # Delay iniziale prima del primo polling
  initial-delay: 1000
  
  # Numero di file da processare in parallelo
  max-concurrent: 3
```

### Come Usare il Polling

1. **Avvia l'applicazione** con `file-polling.enabled: true`
2. **Le directory vengono create automaticamente** all'avvio
3. **Copia i file** (PDF, DOCX, ecc.) nella directory `~/rag-input`
4. **Il sistema li processa automaticamente** ogni 5 secondi
5. **I file processati** vengono spostati in `~/rag-processed`
6. **In caso di errori**, i file finiscono in `~/rag-errors`

```bash
# Esempio di utilizzo
mkdir -p ~/rag-input ~/rag-processed ~/rag-errors

# Copia un documento nella directory monitorata
cp documento.pdf ~/rag-input/

# Il sistema lo processerà automaticamente entro 5 secondi!
# Vedrai nei log:
# 📥 Nuovo file rilevato: documento.pdf
# 🔄 Inizio processamento file: documento.pdf
# ✅ File processato con successo: documento.pdf
# 📁 File spostato in processed-directory
```

### Disabilitare il Polling

Se preferisci usare solo l'API REST per l'upload manuale:

```yaml
file-polling:
  enabled: false
```

## 📋 Prerequisiti

- **Java 17+**
- **Maven 3.6+**
- **Docker & Docker Compose**
- **API Key Google Gemini** (gratuita)

## 🔑 Ottieni API Key Gemini

1. Vai su [Google AI Studio](https://aistudio.google.com/app/apikey)
2. Fai login con il tuo account Google
3. Clicca su "Create API Key"
4. Copia la chiave generata

**È GRATUITA!** Include:
- ✅ 1500 richieste/giorno
- ✅ 1 milione di token/minuto
- ✅ Nessuna carta di credito richiesta

## 🚀 Quick Start

### 1. Configura l'API Key

```bash
# Linux/Mac
export GEMINI_API_KEY=la_tua_api_key_qui

# Windows CMD
set GEMINI_API_KEY=la_tua_api_key_qui

# Windows PowerShell
$env:GEMINI_API_KEY="la_tua_api_key_qui"
```

### 2. Avvia tutto con uno script

```bash
chmod +x start.sh
./start.sh
```

Oppure **manualmente**:

```bash
# 1. Avvia Qdrant
docker-compose up -d

# 2. Compila il progetto
mvn clean package

# 3. Avvia l'applicazione
java -jar target/rag-system-1.0.0.jar
```

L'applicazione sarà disponibile su: **http://localhost:8092**

## 📡 API Endpoints

### 1. Upload Documento

```bash
curl -F "file=@documento.pdf" http://localhost:8092/api/documents/upload
```

**Risposta:**
```json
{
  "message": "✅ Documento caricato e indicizzato con successo!",
  "data": {
    "filename": "documento.pdf",
    "size_bytes": 245678,
    "text_length": 15234,
    "chunks_created": 31,
    "embedding_dimension": 384,
    "status": "success"
  }
}
```

### 2. Fai una Domanda (GET)

```bash
curl "http://localhost:8092/api/query?question=Di%20cosa%20parla%20il%20documento?"
```

### 3. Fai una Domanda (POST)

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

### 4. Health Check

```bash
curl http://localhost:8092/api/documents/health
curl http://localhost:8092/api/query/health
```

## 📂 Formati Supportati

- ✅ **PDF** (.pdf)
- ✅ **Microsoft Word** (.doc, .docx)
- ✅ **Microsoft Excel** (.xls, .xlsx)
- ✅ **Microsoft PowerPoint** (.ppt, .pptx)
- ✅ **Testo** (.txt)
- ✅ **HTML** (.html)
- ✅ **XML** (.xml)

## 🎯 Esempio Completo

### Modalità 1: Upload Manuale via API

```bash
# 1. Avvia il sistema
./start.sh

# 2. Carica un documento
curl -F "file=@manuale_utente.pdf" http://localhost:8092/api/documents/upload

# 3. Fai delle domande
curl "http://localhost:8092/api/query?question=Come%20si%20installa%20il%20software?"

curl "http://localhost:8092/api/query?question=Quali%20sono%20i%20requisiti%20di%20sistema?"

curl "http://localhost:8092/api/query?question=Riassumi%20il%20capitolo%203"
```

### Modalità 2: Auto-Polling (Consigliato!)

```bash
# 1. Avvia il sistema con polling abilitato
./start.sh

# 2. Le directory vengono create automaticamente:
#    ~/rag-input (file da processare)
#    ~/rag-processed (file processati con successo)
#    ~/rag-errors (file con errori)

# 3. Copia i documenti nella directory monitorata
cp documento1.pdf ~/rag-input/
cp contratto.docx ~/rag-input/
cp report.xlsx ~/rag-input/

# Il sistema li processerà automaticamente ogni 5 secondi!
# Nessuna chiamata API necessaria 🎉

# 4. Fai domande su tutti i documenti caricati
curl "http://localhost:8092/api/query?question=Riassumi%20tutti%20i%20documenti"

# 5. Verifica i file processati
ls -la ~/rag-processed/
```

## 🐫 Perché Apache Camel per il Polling?

Apache Camel è la scelta ideale per il polling di directory perché:

✅ **Enterprise Integration Pattern** - Pattern consolidato per integrazione
✅ **Configurazione Dichiarativa** - Route chiare e manutenibili
✅ **Gestione Errori Integrata** - Error handling robusto out-of-the-box
✅ **Filtering Avanzato** - Regex, pattern matching, filtri custom
✅ **Scalabilità** - Thread pool configurabile per processing parallelo
✅ **Movimentazione File** - Move, copy, delete automatici dopo processing
✅ **Monitoring** - Metriche e health checks integrati
✅ **Zero Boilerplate** - Meno codice rispetto a WatchService o Scheduler custom

### Alternative Considerate

| Soluzione | Pro | Contro |
|-----------|-----|--------|
| **Apache Camel** ✅ | Robusto, testato, feature-rich | Dipendenza extra |
| Java WatchService | Nativo, reattivo | Codice boilerplate, meno robusto |
| @Scheduled Spring | Semplice | Meno funzionalità (no move/error handling) |
| Quartz Scheduler | Potente | Overkill per file polling |

**Camel** offre il miglior rapporto funzionalità/complessità per questo use case.

## ⚙️ Configurazione

Modifica `src/main/resources/application.yml`:

```yaml
# Porta dell'applicazione
server:
  port: 8092

# Qdrant
qdrant:
  host: localhost
  port: 6334
  collection-name: documenti

# RAG Settings
rag:
  # Numero di chunks da recuperare per ogni query
  # Valori consigliati:
  #  5-10:  Veloce, buono per documenti semplici
  #  10-20: Più contesto, meglio per domande complesse
  #  20+:   Massimo contesto, ma più lento e più token usati
  top-k: 10

# Gemini
gemini:
  api-key: ${GEMINI_API_KEY}
  model: gemini-2.5-flash  # o gemini-2.0-flash-exp, gemini-1.5-flash, gemini-1.5-pro
  
  # Temperature: controlla la creatività delle risposte
  #   0.0-0.3: Deterministico, preciso, ideale per FAQ/documentazione
  #   0.3-0.7: Bilanciato, buono per RAG generale
  #   0.7-1.0: Creativo, può aggiungere dettagli non nel contesto
  temperature: 0.3
  
  # Max tokens: lunghezza massima della risposta
  #   512-1024:  Risposte brevi e concise
  #   1024-2048: Risposte medie (consigliato per RAG)
  #   2048-4096: Risposte lunghe e dettagliate
  max-tokens: 1024

# Limiti upload
spring:
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB
```

### 🎛️ Tuning dei Parametri

**Temperature:**
- **Bassa (0.0-0.3)**: Risposte precise e deterministiche. Usa quando serve precisione assoluta (FAQ, documentazione tecnica, contratti).
- **Media (0.3-0.7)**: Bilanciato. Buono per la maggior parte dei casi RAG.
- **Alta (0.7-1.0)**: Creativo, può elaborare oltre il contesto. Rischio di "allucinazioni".

**Max Tokens:**
- Dipende dalla complessità delle risposte attese
- Troppo basso → risposte troncate
- Troppo alto → più lento e costoso (ma Gemini è gratuito!)

**Top-K (Chunks):**
- Più chunks = più contesto = risposte migliori MA più lento
- Meno chunks = più veloce MA risposte meno complete
- Dipende dalla dimensione dei tuoi documenti e dalla complessità delle domande
```

## 🔧 Modelli Gemini Disponibili

| Modello | Descrizione | Costo |
|---------|-------------|-------|
| `gemini-2.0-flash-exp` | **Più recente**, veloce e intelligente | GRATIS |
| `gemini-1.5-flash` | Veloce, ottimo per la maggior parte dei casi | GRATIS |
| `gemini-1.5-pro` | Più capace, per task complessi | GRATIS |

## 🗄️ Gestione Qdrant

### Interfaccia Web

Apri il browser su: **http://localhost:6333/dashboard**

### Operazioni CLI

```bash
# Vedi i log
docker-compose logs qdrant

# Riavvia Qdrant
docker-compose restart qdrant

# Ferma tutto
docker-compose down

# Cancella tutti i dati (⚠️ attenzione!)
docker-compose down -v
rm -rf qdrant_storage/
```

## 📊 Come Funziona (Architettura)

```
┌─────────────┐
│   Upload    │
│  PDF/Word   │
└──────┬──────┘
       │
       ▼
┌─────────────────┐
│ Apache Tika     │  ← Estrae il testo
│ (Document       │
│  Parser)        │
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│  Text Splitter  │  ← Divide in chunks (500 caratteri)
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│ AllMiniLmL6V2   │  ← Genera embeddings (vettori)
│ (Embedding      │     LOCALE - niente API!
│  Model)         │
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│    Qdrant       │  ← Salva vettori + testo
│  (Vector DB)    │
└─────────────────┘


Query Flow:
───────────

┌─────────────┐
│  Domanda    │
└──────┬──────┘
       │
       ▼
┌─────────────────┐
│ Embedding       │  ← Trasforma domanda in vettore
│ Model           │
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│ Qdrant Search   │  ← Cerca vettori simili (Top 5)
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│ Build Prompt    │  ← Combina chunks + domanda
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│ Gemini 2.0      │  ← Genera risposta intelligente
│ Flash           │
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│   Risposta +    │
│    Sources      │
└─────────────────┘
```

## 🐛 Troubleshooting

### Errore: "Qdrant connection refused"

```bash
# Verifica che Qdrant sia attivo
docker ps | grep qdrant

# Se non è attivo, avvialo
docker-compose up -d

# Controlla i log
docker-compose logs qdrant
```

### Errore: "Invalid API key"

```bash
# Verifica che l'API key sia impostata
echo $GEMINI_API_KEY

# Se vuota, impostala
export GEMINI_API_KEY=la_tua_api_key
```

### Errore: "File too large"

Modifica `application.yml`:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 200MB  # Aumenta il limite
      max-request-size: 200MB
```

### Errore di compilazione

```bash
# Pulisci e ricompila
mvn clean package -DskipTests -U
```

## 📈 Performance

- **Embedding Model**: AllMiniLmL6V2 (locale, 384 dimensioni)
  - ⚡ Veloce: ~1000 chunks/secondo
  - 💾 Leggero: ~100MB RAM
  - 🆓 Gratis: nessuna API call

- **Gemini 2.0 Flash**:
  - ⚡ Latenza: ~1-2 secondi
  - 💰 Costi: GRATIS fino a 1500 req/giorno
  - 🎯 Qualità: Eccellente per RAG

- **Qdrant**:
  - 🚀 Ricerca: <100ms per 100k vettori
  - 💾 Storage: Efficiente e persistente

## 🔐 Sicurezza

- ✅ API key tramite variabile d'ambiente
- ✅ Nessuna API key hardcoded nel codice
- ✅ Validazione input sui file
- ⚠️ In produzione aggiungi autenticazione (OAuth2, JWT)

## 📝 Note

- Gli embeddings sono generati **localmente** (gratuito, nessuna API call)
- Solo le query a Gemini richiedono API calls
- I dati in Qdrant persistono anche dopo il riavvio
- Il file temporaneo viene cancellato dopo il processamento

## 🆘 Supporto

Per problemi o domande:
1. Controlla i log: `docker-compose logs` e log dell'applicazione
2. Verifica la configurazione in `application.yml`
3. Prova con un file di test piccolo (< 1MB)

## 📜 Licenza

Questo progetto è fornito "as-is" per scopi educativi e di sviluppo.
