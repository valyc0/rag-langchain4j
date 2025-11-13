# 🧪 Esempi Curl - Sistema RAG

Questa guida contiene tutti i comandi `curl` per testare il sistema RAG.

## 📋 Prerequisiti

1. Il sistema deve essere avviato: `./start.sh`
2. L'applicazione gira su: `http://localhost:8092`

---

## 🔍 Health Check

Verifica che l'applicazione sia attiva:

```bash
# Health check documenti
curl http://localhost:8092/api/documents/health

# Health check query
curl http://localhost:8092/api/query/health
```

**Risposta attesa:**
```json
{
  "status": "UP",
  "service": "Document Processing API"
}
```

---

## 📚 Lista Documenti Indicizzati

Visualizza tutti i documenti caricati nel sistema:

```bash
curl http://localhost:8092/api/documents/list
```

**Risposta attesa:**
```json
{
  "total_documents": 2,
  "total_chunks": 148,
  "documents": {
    "Lavventura-di-Oliver.pdf": 148,
    "02 Inferno.pdf": 1548
  },
  "timestamps": {
    "Lavventura-di-Oliver.pdf": 1731534567890,
    "02 Inferno.pdf": 1731536912203
  }
}
```

**Formatta con jq:**
```bash
curl -s http://localhost:8092/api/documents/list | jq
```

---

## 📤 Upload Documenti

### Upload file PDF

```bash
curl -X POST http://localhost:8092/api/documents/upload \
  -F "file=@documento.pdf"
```

### Upload file Word

```bash
curl -X POST http://localhost:8092/api/documents/upload \
  -F "file=@documento.docx"
```

### Upload file TXT

```bash
curl -X POST http://localhost:8092/api/documents/upload \
  -F "file=@documento.txt"
```

### Upload file Excel

```bash
curl -X POST http://localhost:8092/api/documents/upload \
  -F "file=@dati.xlsx"
```

### Upload file PowerPoint

```bash
curl -X POST http://localhost:8092/api/documents/upload \
  -F "file=@presentazione.pptx"
```

**Risposta attesa:**
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

---

## ❓ Query RAG

### Query GET (semplice)

```bash
# Domanda base
curl "http://localhost:8092/api/query?question=Di%20cosa%20parla%20il%20documento?"

# Con caratteri speciali (URL encoded)
curl "http://localhost:8092/api/query?question=Quali%20sono%20i%20punti%20principali%3F"

# Riassunto
curl "http://localhost:8092/api/query?question=Riassumi%20il%20contenuto"

# Domanda specifica
curl "http://localhost:8092/api/query?question=Come%20si%20installa%20il%20software?"
```

### Query POST (con JSON)

```bash
# Domanda semplice
curl -X POST http://localhost:8092/api/query \
  -H "Content-Type: application/json" \
  -d '{"question": "Di cosa parla il documento?"}'

# Domanda complessa
curl -X POST http://localhost:8092/api/query \
  -H "Content-Type: application/json" \
  -d '{"question": "Quali sono i requisiti di sistema e le procedure di installazione?"}'

# Richiesta di sintesi
curl -X POST http://localhost:8092/api/query \
  -H "Content-Type: application/json" \
  -d '{"question": "Riassumi i punti principali del documento in 5 bullet points"}'

# Domanda su dati specifici
curl -X POST http://localhost:8092/api/query \
  -H "Content-Type: application/json" \
  -d '{"question": "Quali sono i costi indicati nel documento?"}'
```

**Risposta attesa:**
```json
{
  "answer": "Il documento tratta principalmente di...",
  "sources": [
    {
      "text": "Questo è il testo del chunk rilevante estratto dal documento...",
      "score": 0.8945,
      "filename": "documento.pdf"
    },
    {
      "text": "Un altro chunk rilevante con informazioni correlate...",
      "score": 0.8234,
      "filename": "documento.pdf"
    }
  ],
  "question": "Di cosa parla il documento?",
  "chunks_used": 5
}
```

---

## 🎯 Esempi Pratici Completi

### Scenario 1: Analisi di un Manuale Utente

```bash
# 1. Carica il manuale
curl -F "file=@manuale_utente.pdf" \
  http://localhost:8092/api/documents/upload

# 2. Domande specifiche
curl "http://localhost:8092/api/query?question=Come%20si%20installa?"

curl "http://localhost:8092/api/query?question=Quali%20sono%20i%20requisiti%20minimi?"

curl "http://localhost:8092/api/query?question=Come%20si%20risolve%20l%27errore%20404?"
```

### Scenario 2: Analisi Contratto/Documento Legale

```bash
# 1. Carica il contratto
curl -F "file=@contratto.pdf" \
  http://localhost:8092/api/documents/upload

# 2. Analisi clausole
curl -X POST http://localhost:8092/api/query \
  -H "Content-Type: application/json" \
  -d '{"question": "Quali sono le clausole di risoluzione del contratto?"}'

curl -X POST http://localhost:8092/api/query \
  -H "Content-Type: application/json" \
  -d '{"question": "Quali sono gli obblighi delle parti?"}'

curl -X POST http://localhost:8092/api/query \
  -H "Content-Type: application/json" \
  -d '{"question": "Quali sono le penali previste?"}'
```

### Scenario 3: Analisi Report Finanziario

```bash
# 1. Carica il report Excel o PDF
curl -F "file=@report_finanziario.xlsx" \
  http://localhost:8092/api/documents/upload

# 2. Domande sui dati
curl "http://localhost:8092/api/query?question=Qual%20%C3%A8%20il%20fatturato%20totale?"

curl "http://localhost:8092/api/query?question=Quali%20sono%20i%20principali%20costi?"

curl -X POST http://localhost:8092/api/query \
  -H "Content-Type: application/json" \
  -d '{"question": "Confronta le performance del Q1 e Q2"}'
```

### Scenario 4: Analisi Documentazione Tecnica

```bash
# 1. Carica la documentazione
curl -F "file=@api_documentation.pdf" \
  http://localhost:8092/api/documents/upload

# 2. Domande tecniche
curl "http://localhost:8092/api/query?question=Come%20funziona%20l%27autenticazione?"

curl "http://localhost:8092/api/query?question=Quali%20sono%20gli%20endpoint%20disponibili?"

curl -X POST http://localhost:8092/api/query \
  -H "Content-Type: application/json" \
  -d '{"question": "Mostrami un esempio di chiamata API per creare un utente"}'
```

---

## 📊 Formattazione Risposta (con jq)

Se hai `jq` installato, puoi formattare le risposte JSON:

### Mostra solo la risposta

```bash
curl -s "http://localhost:8092/api/query?question=Di%20cosa%20parla?" | jq -r '.answer'
```

### Mostra risposta e fonti

```bash
curl -s "http://localhost:8092/api/query?question=Riassumi" | jq '{risposta: .answer, fonti: .sources}'
```

### Mostra solo i primi 100 caratteri della risposta

```bash
curl -s "http://localhost:8092/api/query?question=Test" | jq -r '.answer' | cut -c1-100
```

### Conta quante fonti sono state usate

```bash
curl -s "http://localhost:8092/api/query?question=Test" | jq '.sources | length'
```

### Mostra i punteggi di rilevanza

```bash
curl -s "http://localhost:8092/api/query?question=Test" | jq '.sources[] | {score: .score, filename: .filename}'
```

---

## 🧪 Test Completo Automatico

### Script di test rapido

Salva questo in un file `quick_test.sh`:

```bash
#!/bin/bash

echo "🧪 Test Rapido Sistema RAG"
echo ""

# 1. Health check
echo "1. Health check..."
curl -s http://localhost:8092/api/documents/health | jq '.'
echo ""

# 2. Crea file di test
echo "2. Creazione file di test..."
cat > test_doc.txt << 'EOF'
Questo è un documento di test per il sistema RAG.
Il sistema utilizza Qdrant come vector database e Gemini 2.0 Flash come LLM.
L'applicazione è scritta in Java con Spring Boot 3.2.0.
Apache Tika viene utilizzato per estrarre il testo dai documenti.
Gli embeddings sono generati con il modello AllMiniLmL6V2 che è completamente locale e gratuito.
EOF
echo "✅ File creato"
echo ""

# 3. Upload
echo "3. Upload documento..."
curl -s -F "file=@test_doc.txt" http://localhost:8092/api/documents/upload | jq '.'
echo ""

# 4. Attendi indicizzazione
echo "4. Attesa indicizzazione (3 secondi)..."
sleep 3
echo ""

# 5. Query
echo "5. Query: Cos'è questo sistema?"
curl -s "http://localhost:8092/api/query?question=Cos%27%C3%A8%20questo%20sistema%3F" | jq -r '.answer'
echo ""
echo ""

echo "6. Query: Quale database viene usato?"
curl -s "http://localhost:8092/api/query?question=Quale%20database%20viene%20usato%3F" | jq -r '.answer'
echo ""
echo ""

echo "7. Query: Con cosa sono generati gli embeddings?"
curl -s "http://localhost:8092/api/query?question=Con%20cosa%20sono%20generati%20gli%20embeddings%3F" | jq -r '.answer'
echo ""

# Cleanup
rm -f test_doc.txt

echo ""
echo "✅ Test completato!"
```

Rendi eseguibile ed esegui:

```bash
chmod +x quick_test.sh
./quick_test.sh
```

---

## 📝 Note sull'URL Encoding

Quando usi query GET, i caratteri speciali devono essere codificati:

| Carattere | Codifica | Esempio |
|-----------|----------|---------|
| Spazio | `%20` | `Come si installa` → `Come%20si%20installa` |
| `?` | `%3F` | `Come funziona?` → `Come%20funziona%3F` |
| `&` | `%26` | `A & B` → `A%20%26%20B` |
| `=` | `%3D` | `x = 5` → `x%20%3D%205` |
| `'` | `%27` | `Cos'è` → `Cos%27%C3%A8` |
| `è` | `%C3%A8` | `è` → `%C3%A8` |
| `à` | `%C3%A0` | `à` → `%C3%A0` |

**Tip**: Usa lo script Python per codificare automaticamente:

```bash
python3 -c "import urllib.parse; print(urllib.parse.quote('Come funziona?'))"
```

Oppure usa la versione POST che non richiede encoding!

---

## ⚠️ Troubleshooting

### Errore: Connection refused

```bash
# Verifica che l'app sia attiva
curl http://localhost:8092/api/documents/health

# Se fallisce, avvia con:
./start.sh
```

### Errore: File non trovato

```bash
# Usa percorso assoluto o relativo corretto
curl -F "file=@/percorso/completo/al/file.pdf" http://localhost:8092/api/documents/upload

# Oppure naviga nella directory del file
cd /cartella/con/documenti
curl -F "file=@documento.pdf" http://localhost:8092/api/documents/upload
```

### Errore: No documents found

```bash
# Verifica che Qdrant sia attivo
docker ps | grep qdrant

# Ricarica un documento
curl -F "file=@documento.pdf" http://localhost:8092/api/documents/upload
```

---

## 🎓 Tips & Best Practices

1. **Upload multipli**: Carica più documenti per avere un contesto più ampio
2. **Domande specifiche**: Più la domanda è precisa, migliore sarà la risposta
3. **Verifica le fonti**: Controlla il campo `sources` per vedere da dove viene la risposta
4. **Score di rilevanza**: Punteggi > 0.8 indicano alta rilevanza
5. **POST vs GET**: Usa POST per domande complesse o con caratteri speciali

---

## 📞 Supporto

Per problemi o domande, controlla:
- Log dell'applicazione: nella console dove hai avviato `./start.sh`
- Log di Qdrant: `docker-compose logs qdrant`
- README principale: `README.md`
