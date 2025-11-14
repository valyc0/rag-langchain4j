#!/bin/bash

# Script di test per il meccanismo di auto-polling
# Crea directory di test e simula l'arrivo di file

echo "🧪 Test Auto-Polling RAG System"
echo "================================"
echo ""

# Directory di test (personalizza se necessario)
TEST_INPUT_DIR="$HOME/rag-input"
TEST_PROCESSED_DIR="$HOME/rag-processed"
TEST_ERROR_DIR="$HOME/rag-errors"

# Crea directory se non esistono
echo "📁 Creazione directory di test..."
mkdir -p "$TEST_INPUT_DIR"
mkdir -p "$TEST_PROCESSED_DIR"
mkdir -p "$TEST_ERROR_DIR"
echo "✅ Directory create:"
echo "   Input: $TEST_INPUT_DIR"
echo "   Processed: $TEST_PROCESSED_DIR"
echo "   Errors: $TEST_ERROR_DIR"
echo ""

# Crea un file di test (TXT)
echo "📝 Creazione file di test..."
TEST_FILE="$TEST_INPUT_DIR/test-document-$(date +%Y%m%d-%H%M%S).txt"
cat > "$TEST_FILE" << 'EOF'
Sistema RAG - Documento di Test
================================

Questo è un documento di test per verificare il funzionamento del sistema RAG
con polling automatico delle directory.

Capitolo 1: Introduzione
------------------------
Il sistema RAG (Retrieval-Augmented Generation) permette di interrogare
documenti usando l'intelligenza artificiale.

Capitolo 2: Funzionalità
-----------------------
- Upload manuale via API REST
- Polling automatico di directory con Apache Camel
- Indicizzazione in Qdrant vector database
- Query intelligenti con Google Gemini

Capitolo 3: Tecnologie
---------------------
- Spring Boot 3.2.0
- LangChain4j per orchestrazione RAG
- Apache Camel per file polling
- Qdrant per vector storage
- Google Gemini 2.0 Flash come LLM

Domande di Test:
1. Quali sono le principali funzionalità del sistema?
2. Quali tecnologie vengono utilizzate?
3. Cos'è il RAG?
EOF

echo "✅ File creato: $TEST_FILE"
echo ""

# Verifica che l'applicazione sia in esecuzione
echo "🔍 Verifica applicazione..."
if curl -s http://localhost:8092/api/documents/health > /dev/null 2>&1; then
    echo "✅ Applicazione in esecuzione su porta 8092"
    echo ""
    
    echo "⏳ Il file verrà processato automaticamente entro 5 secondi..."
    echo "   Monitora i log dell'applicazione per vedere:"
    echo "   - 📥 Nuovo file rilevato: test-document-*.txt"
    echo "   - 🔄 Inizio processamento file"
    echo "   - ✅ File processato con successo"
    echo ""
    
    echo "📊 Dopo il processamento, prova queste query:"
    echo ""
    echo "curl \"http://localhost:8092/api/query?question=Cos%27%C3%A8%20il%20RAG?\""
    echo ""
    echo "curl \"http://localhost:8092/api/query?question=Quali%20tecnologie%20usa%20il%20sistema?\""
    echo ""
    echo "curl \"http://localhost:8092/api/query?question=Quali%20sono%20le%20funzionalit%C3%A0?\""
    echo ""
    
    # Aspetta un po' per dare tempo al polling
    echo "⏱️  Attendo 10 secondi per il processamento..."
    sleep 10
    
    # Verifica se il file è stato spostato
    if [ -f "$TEST_PROCESSED_DIR/$(basename $TEST_FILE)" ]; then
        echo "✅ SUCCESS! File processato e spostato in $TEST_PROCESSED_DIR"
        echo ""
        echo "🎉 Test AUTO-POLLING completato con successo!"
        echo ""
        echo "🧪 Prova ora una query:"
        curl -s "http://localhost:8092/api/query?question=Cos'è%20il%20RAG?" | jq '.'
    else
        echo "⚠️  File non ancora processato. Controlla i log dell'applicazione."
        echo "   Il file potrebbe essere in coda o in elaborazione."
        echo ""
        echo "   Verifica manualmente:"
        echo "   ls -la $TEST_PROCESSED_DIR/"
        echo "   ls -la $TEST_ERROR_DIR/"
    fi
else
    echo "❌ Applicazione NON in esecuzione!"
    echo ""
    echo "Avvia l'applicazione con:"
    echo "  ./start.sh"
    echo ""
    echo "Oppure manualmente:"
    echo "  docker-compose up -d"
    echo "  mvn clean package"
    echo "  java -jar target/rag-system-1.0.0.jar"
fi

echo ""
echo "📁 Puoi copiare altri file in $TEST_INPUT_DIR per testarli"
echo "   Formati supportati: PDF, DOC, DOCX, XLS, XLSX, PPT, PPTX, TXT, HTML, XML"
