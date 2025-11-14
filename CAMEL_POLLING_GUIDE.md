# Apache Camel File Polling - Configurazione Avanzata

## 📚 Guida Completa al File Polling

Questo documento descrive in dettaglio il meccanismo di auto-polling implementato con Apache Camel.

## 🏗️ Architettura

```
┌─────────────────┐
│   Input Dir     │  ← Monitored directory
│  ~/rag-input    │     (configurable)
└────────┬────────┘
         │
         │ Apache Camel File Component
         │ (polling every 5s)
         ▼
┌──────────────────┐
│ FilePollingRoute │  ← Camel Route
│   (Filter by     │    - Applies regex filter
│    pattern)      │    - Thread pool (max 3)
└────────┬─────────┘    - Error handling
         │
         ▼
┌──────────────────┐
│ FileProcessorBean│  ← Spring Bean
│   (Convert to    │    - Wraps File as MultipartFile
│    MultipartFile)│    - Calls DocumentProcessingService
└────────┬─────────┘
         │
         ▼
┌────────────────────────┐
│DocumentProcessingService│  ← RAG Processing
│  - Apache Tika         │    - Extract text
│  - Text Splitter       │    - Split into chunks
│  - Embedding Model     │    - Generate embeddings
│  - Qdrant Store        │    - Save to vector DB
└────────┬───────────────┘
         │
         ├─ Success ──► Processed Dir (~/rag-processed)
         │
         └─ Error ────► Error Dir (~/rag-errors)
```

## ⚙️ Parametri di Configurazione

### `file-polling.enabled`

**Tipo:** `boolean`  
**Default:** `false`  
**Descrizione:** Abilita o disabilita completamente il polling

```yaml
file-polling:
  enabled: true  # Attiva il polling
```

Se `false`, la route Camel non viene creata (thanks to `@ConditionalOnProperty`).

---

### `file-polling.input-directory`

**Tipo:** `String` (path)  
**Default:** `${HOME}/rag-input`  
**Descrizione:** Directory da monitorare per nuovi file

```yaml
file-polling:
  input-directory: /data/documents/inbox
```

**Note:**
- Può essere assoluto o relativo
- Usa `${HOME}` per la home directory dell'utente
- La directory viene creata automaticamente se non esiste

---

### `file-polling.processed-directory`

**Tipo:** `String` (path)  
**Default:** `${HOME}/rag-processed`  
**Descrizione:** Dove spostare i file dopo processamento con successo

```yaml
file-polling:
  processed-directory: /data/documents/processed
```

**Note:**
- I file vengono **spostati** (non copiati)
- Mantiene il nome originale del file
- La directory viene creata automaticamente

---

### `file-polling.error-directory`

**Tipo:** `String` (path)  
**Default:** `${HOME}/rag-errors`  
**Descrizione:** Dove spostare i file che hanno generato errori

```yaml
file-polling:
  error-directory: /data/documents/errors
```

**Note:**
- Attivato da `.onException(Exception.class)` nella route
- Utile per debugging e retry manuali
- La directory viene creata automaticamente

---

### `file-polling.delay`

**Tipo:** `int` (millisecondi)  
**Default:** `5000` (5 secondi)  
**Descrizione:** Intervallo di tempo tra ogni polling

```yaml
file-polling:
  delay: 10000  # Controlla ogni 10 secondi
```

**Valori consigliati:**
- **1000-3000ms**: Risposta veloce (high-frequency polling)
- **5000-10000ms**: Bilanciato (recommended)
- **30000-60000ms**: Low-frequency (risparmio risorse)

---

### `file-polling.initial-delay`

**Tipo:** `int` (millisecondi)  
**Default:** `1000` (1 secondo)  
**Descrizione:** Attesa prima del primo polling all'avvio

```yaml
file-polling:
  initial-delay: 5000  # Aspetta 5 secondi dopo l'avvio
```

**Perché è utile?**
- Dà tempo a Spring Boot di completare l'inizializzazione
- Evita race conditions all'avvio
- Permette al sistema di "stabilizzarsi"

---

### `file-polling.file-pattern`

**Tipo:** `String` (Java Regex)  
**Default:** `.*\\.(pdf|doc|docx|xls|xlsx|ppt|pptx|txt|html|xml)$`  
**Descrizione:** Regex per filtrare i file da processare

```yaml
file-polling:
  # Solo PDF
  file-pattern: .*\\.pdf$
  
  # PDF e Word
  file-pattern: .*\\.(pdf|docx?)$
  
  # Tutti i file (sconsigliato!)
  file-pattern: .*
```

**Note:**
- Usa `\\` per escape in YAML
- Case-insensitive nel codice Camel (`.include()` parameter)
- Validazione fatta PRIMA del processamento

---

### `file-polling.max-concurrent`

**Tipo:** `int`  
**Default:** `3`  
**Descrizione:** Numero massimo di file processati in parallelo

```yaml
file-polling:
  max-concurrent: 5  # Fino a 5 file contemporaneamente
```

**Tuning:**
- **1**: Sequenziale, ordine garantito, lento
- **2-3**: Bilanciato (default)
- **5-10**: Veloce, richiede più RAM/CPU
- **10+**: Solo per hardware potente

**Attenzione:** Ogni file usa:
- ~100MB RAM per embedding model
- CPU per Tika parsing
- Network I/O verso Qdrant

---

## 🔧 Esempi di Configurazione

### 1. Development (veloce e reattivo)

```yaml
file-polling:
  enabled: true
  input-directory: ./test-input
  processed-directory: ./test-processed
  error-directory: ./test-errors
  delay: 2000
  initial-delay: 500
  file-pattern: .*\\.(pdf|txt)$
  max-concurrent: 1
```

### 2. Production (bilanciato)

```yaml
file-polling:
  enabled: true
  input-directory: /var/rag/input
  processed-directory: /var/rag/processed
  error-directory: /var/rag/errors
  delay: 5000
  initial-delay: 2000
  file-pattern: .*\\.(pdf|doc|docx|xls|xlsx|ppt|pptx|txt|html|xml)$
  max-concurrent: 3
```

### 3. High-Throughput (massima velocità)

```yaml
file-polling:
  enabled: true
  input-directory: /data/bulk-import
  processed-directory: /data/processed
  error-directory: /data/errors
  delay: 1000
  initial-delay: 1000
  file-pattern: .*\\.(pdf|docx)$
  max-concurrent: 10
```

---

## 🎛️ Monitoring e Debug

### Log da cercare

```log
# Avvio route
🚀 Avvio route di polling file
📂 Directory input: /home/user/rag-input
⏱️  Polling delay: 5000ms

# File rilevato
📥 Nuovo file rilevato: documento.pdf

# Processing
🔄 Inizio processamento file: documento.pdf (245678 bytes)
✅ File processato con successo: documento.pdf
📊 Statistiche: chunks=31, embedding_dim=384

# Spostamento
📁 File spostato in processed-directory: documento.pdf

# Errore
❌ Errore nel processamento di corrupt.pdf: Invalid PDF structure
📁 File spostato in error-directory: corrupt.pdf
```

### Health Check

```bash
# Verifica che l'app sia up
curl http://localhost:8092/api/documents/health

# Verifica Camel routes (se JMX abilitato)
curl http://localhost:8092/actuator/camelroutes
```

---

## 🚨 Troubleshooting

### Problema: File non vengono processati

**Diagnosi:**
```bash
# Verifica che enabled=true
grep "file-polling.enabled" application.yml

# Verifica log all'avvio
grep "Avvio route di polling" logs/application.log

# Verifica permessi directory
ls -la ~/rag-input
```

**Soluzioni:**
1. Controlla `file-polling.enabled: true`
2. Verifica che il file pattern sia corretto
3. Controlla i permessi delle directory

---

### Problema: File processati troppo lentamente

**Soluzioni:**
1. Riduci `delay`: `delay: 2000`
2. Aumenta `max-concurrent`: `max-concurrent: 5`
3. Usa hardware più potente (più RAM/CPU)

---

### Problema: File finiscono in error-directory

**Diagnosi:**
```bash
# Controlla i log per vedere l'errore
grep "Errore nel processamento" logs/application.log

# Prova a processare manualmente via API
curl -F "file=@~/rag-errors/problema.pdf" http://localhost:8092/api/documents/upload
```

**Cause comuni:**
- File corrotto
- Formato non supportato da Tika
- File troppo grande (aumenta `spring.servlet.multipart.max-file-size`)
- Qdrant non disponibile

---

## 🔒 Best Practices

### 1. Sicurezza

```yaml
# NON usare directory condivise senza protezione
file-polling:
  input-directory: /secure/rag-input  # Con permessi 700
```

### 2. Performance

```yaml
# Bilancia delay vs throughput
# Delay basso = più CPU ma risposta veloce
# Delay alto = meno CPU ma risposta lenta
file-polling:
  delay: 5000
  max-concurrent: 3  # Non esagerare!
```

### 3. Resilienza

```yaml
# Sempre specificare error-directory
file-polling:
  error-directory: /var/rag/errors  # Per analisi manuale
```

### 4. Monitoring

```yaml
# Abilita logging dettagliato
logging:
  level:
    com.example.rag.camel: DEBUG
    org.apache.camel: INFO
```

---

## 📦 Integrazione con Sistemi Esterni

### Ricevere file da FTP

```yaml
# Usa un cron job o altro processo per scaricare da FTP
# e copiarli in input-directory
```

```bash
#!/bin/bash
# download-from-ftp.sh
lftp -c "open ftp://server; mirror /remote/docs ~/rag-input"
```

### Ricevere file da Email (con allegati)

Usa uno script Python con `imaplib` per:
1. Connettersi alla mailbox
2. Scaricare allegati
3. Salvarli in `input-directory`

### Ricevere file da S3/MinIO

```bash
# Usa s3cmd o aws cli
aws s3 sync s3://bucket/documents ~/rag-input
```

---

## 🎓 Alternative a Camel (per confronto)

### Java WatchService (Nativo)

```java
// Più verboso, meno funzionalità
WatchService watchService = FileSystems.getDefault().newWatchService();
path.register(watchService, ENTRY_CREATE);
// ... più codice per gestire eventi, errori, move, etc.
```

### Spring @Scheduled

```java
@Scheduled(fixedDelay = 5000)
public void pollFiles() {
    // Devi implementare tutto: scan, filter, move, error handling
    File[] files = new File(inputDir).listFiles();
    // ... logica manuale
}
```

**Perché Camel è meglio:**
- ✅ Meno codice boilerplate
- ✅ Error handling integrato
- ✅ Move/copy/delete automatici
- ✅ Pattern matching avanzato
- ✅ Thread pool configurabile
- ✅ Monitoring e metriche

---

## 📚 Riferimenti

- [Apache Camel File Component](https://camel.apache.org/components/latest/file-component.html)
- [Spring Boot + Camel Integration](https://camel.apache.org/camel-spring-boot/latest/)
- [Enterprise Integration Patterns](https://www.enterpriseintegrationpatterns.com/)
