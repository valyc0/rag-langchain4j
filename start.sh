#!/bin/bash

echo "🚀 Avvio Sistema RAG"
echo ""

# 1. Avvia Qdrant
echo "1️⃣  Avvio Qdrant..."
docker-compose up -d

# 2. Attendi che Qdrant sia pronto
echo "⏳ Attendo 5 secondi..."
sleep 5

# 3. Crea collezione se non esiste
echo "2️⃣  Controllo collezione..."

# Verifica se la collezione esiste
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:6333/collections/documenti)

if [ "$HTTP_CODE" = "404" ] || [ "$HTTP_CODE" = "000" ]; then
    echo "📦 Creo collezione 'documenti'..."
    curl -X PUT http://localhost:6333/collections/documenti \
      -H "Content-Type: application/json" \
      -d '{"vectors": {"size": 384, "distance": "Cosine"}}'
    echo ""
    echo "✅ Collezione creata!"
else
    echo "✅ Collezione già esistente!"
fi

echo ""
echo "3️⃣  Configurazione Gemini API Key..."
echo ""

# Chiedi la chiave API se non è già impostata
if [ -z "$GEMINI_API_KEY" ]; then
    read -p "🔑 Inserisci la tua Gemini API Key: " GEMINI_API_KEY
    export GEMINI_API_KEY
fi

echo ""
echo "4️⃣  Avvio Spring Boot..."
echo ""

# Avvia Spring Boot
mvn spring-boot:run
