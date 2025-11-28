# Guida: Upload Asincrono con Polling

## Novità Implementate

Il sistema RAG è stato aggiornato per supportare l'**upload asincrono** dei documenti con **polling automatico** dello stato.

### Cosa è cambiato:

#### Backend (`/rag-langchain4j`)

1. **Processamento Asincrono**
   - L'upload del file ritorna immediatamente con status `PROCESSING`
   - Il processamento avviene in background in un thread pool dedicato
   - Supporta fino a 5 documenti in parallelo

2. **Stati dei Documenti**
   - `PROCESSING` - Documento in elaborazione
   - `READY` - Documento pronto per le query
   - `ERROR` - Errore durante l'elaborazione

3. **Nuovi Endpoint**
   - `GET /api/documents/status/{filename}` - Stato di un documento specifico
   - `GET /api/documents/statuses` - Stati di tutti i documenti

4. **Nuovi File Creati**
   - `DocumentStatus.java` - Enum con gli stati possibili
   - `DocumentInfo.java` - DTO per informazioni documento
   - `DocumentStatusService.java` - Servizio per gestire gli stati
   - `AsyncConfig.java` - Configurazione thread pool per elaborazione asincrona

5. **File Modificati**
   - `DocumentProcessingService.java` - Aggiunto metodo `processDocumentAsync()`
   - `DocumentController.java` - Endpoint upload aggiornato + nuovi endpoint per stati

#### Client Web (`/client-web`)

1. **Feedback Immediato**
   - Mostra "✅ Upload completato!" appena il file viene caricato
   - Non aspetta più il completamento del processamento

2. **Polling Automatico**
   - Aggiorna la grid ogni 2 secondi
   - Si attiva automaticamente quando ci sono documenti in `PROCESSING`
   - Si ferma quando tutti i documenti sono `READY` o `ERROR`

3. **Visualizzazione Stati**
   - Colonna "🔄 Stato" nella grid mostra:
     - "⏳ In elaborazione..." per documenti in processamento
     - "✅ Pronto" per documenti pronti
     - "❌ Errore" per documenti con errori
   - Pulsante "Elimina" abilitato solo per documenti `READY`

4. **Nuovi File Creati**
   - `DocumentStatusResponse.java` - DTO per stato documento
   - `AllStatusesResponse.java` - DTO per tutti gli stati

5. **File Modificati**
   - `DocumentsView.java` - Implementato polling e gestione stati
   - `RagApiService.java` - Aggiunti metodi per chiamare nuovi endpoint

## Come Funziona il Flusso

```
1. Utente carica un file
   ↓
2. Client invia file al backend
   ↓
3. Backend salva il file e registra lo stato PROCESSING
   ↓
4. Backend ritorna immediatamente: "Upload completato!"
   ↓
5. Client mostra notifica "done" ✅
   ↓
6. Client avvia polling ogni 2 secondi
   ↓
7. Backend processa file in background (estrazione, chunking, embeddings)
   ↓
8. Durante il polling, client aggiorna la grid mostrando:
   - "⏳ In elaborazione..." mentre il file è in PROCESSING
   ↓
9. Quando il processamento finisce:
   - Backend aggiorna stato a READY (o ERROR se fallisce)
   ↓
10. Al prossimo polling:
    - Client vede stato READY
    - Mostra "✅ Pronto" nella grid
    - Ferma il polling (se non ci sono altri documenti in PROCESSING)
```

## Vantaggi

✅ **UX Migliorata**: Feedback immediato all'utente  
✅ **Non Bloccante**: Backend può processare documenti grandi senza timeout  
✅ **Scalabile**: Thread pool gestisce più upload simultanei  
✅ **Trasparente**: L'utente vede sempre lo stato attuale  
✅ **Automatico**: Polling si attiva/disattiva automaticamente  

## Configurazione

### Backend - Thread Pool

Modifica `AsyncConfig.java`:

```java
executor.setCorePoolSize(2);    // Threads sempre attivi
executor.setMaxPoolSize(5);     // Massimo threads
executor.setQueueCapacity(100); // Documenti in coda
```

### Client - Frequenza Polling

Modifica `DocumentsView.java`:

```java
pollingScheduler.scheduleAtFixedRate(() -> {
    // ...
}, 2, 2, TimeUnit.SECONDS); // Intervallo in secondi
```

## Test

### 1. Avvia Backend
```bash
cd /home/valyc-pc/lavoro/rag-langchain4j
./start.sh
```

### 2. Avvia Client
```bash
cd /home/valyc-pc/lavoro/rag-langchain4j/client-web
./start.sh
```

### 3. Test Upload
1. Apri http://localhost:8093
2. Vai su "Documenti"
3. Carica un file PDF
4. Verifica:
   - Vedi subito "✅ Upload completato!"
   - La grid mostra "⏳ In elaborazione..."
   - Dopo qualche secondo cambia in "✅ Pronto"
   - Il pulsante "Elimina" diventa abilitato

### 4. Test Upload Multipli
1. Carica 3-4 file in sequenza veloce
2. Verifica che vengano tutti processati
3. Osserva la grid aggiornarsi automaticamente

## Monitoraggio

Nei log del backend vedrai:

```
📝 Documento registrato in PROCESSING: documento.pdf
📄 Inizio processamento asincrono documento: documento.pdf
✅ Testo estratto: 15234 caratteri
✂️ Documento diviso in 31 chunks
🔢 Embeddings generati: 31 vettori di 384 dimensioni
💾 Salvato in Qdrant!
✅ Documento marcato come READY: documento.pdf (31 chunks)
```

Nel client vedrai:

```
🔄 Polling avviato per aggiornare stato documenti
⏸️ Polling fermato (tutti i documenti sono pronti)
```

## Troubleshooting

### Il polling non si ferma
- Verifica che i documenti siano effettivamente in stato READY
- Controlla i log del backend per errori

### Gli stati non si aggiornano
- Verifica che il backend sia raggiungibile
- Controlla la console browser per errori REST

### Il processamento è troppo lento
- Aumenta `maxPoolSize` in `AsyncConfig.java`
- Riduci `chunkSize` in `application.yml` per creare meno chunks

## API Reference

### GET /api/documents/statuses
Ritorna tutti gli stati dei documenti:

```json
{
  "documento.pdf": {
    "status": "READY",
    "chunks": 31,
    "uploadTimestamp": 1732825680000,
    "readyTimestamp": 1732825685000
  },
  "contratto.docx": {
    "status": "PROCESSING",
    "chunks": 0,
    "uploadTimestamp": 1732825690000
  }
}
```

### GET /api/documents/status/{filename}
Ritorna lo stato di un documento specifico:

```json
{
  "filename": "documento.pdf",
  "status": "READY",
  "chunks": 31,
  "uploadTimestamp": 1732825680000,
  "readyTimestamp": 1732825685000
}
```

Se il documento è in errore:

```json
{
  "filename": "documento_corrotto.pdf",
  "status": "ERROR",
  "chunks": 0,
  "uploadTimestamp": 1732825700000,
  "errorMessage": "Il documento non contiene testo estraibile..."
}
```
