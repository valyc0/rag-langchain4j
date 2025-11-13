#!/bin/bash

echo "🧪 Test Sistema RAG"
echo "===================="
echo ""

# Verifica che l'applicazione sia attiva
if ! curl -s http://localhost:8092/api/documents/health > /dev/null; then
    echo "❌ L'applicazione non è attiva. Avviala prima con ./start.sh"
    exit 1
fi

echo "✅ Applicazione attiva!"
echo ""

# Crea un file di test
echo "Il gatto è un animale domestico molto popolare. I gatti sono carnivori e amano cacciare. Molti gatti domestici vivono in casa e dormono per 12-16 ore al giorno." > test_document.txt

echo "1️⃣  Upload documento di test..."
UPLOAD_RESPONSE=$(curl -s -F "file=@test_document.txt" http://localhost:8092/api/documents/upload)
echo "$UPLOAD_RESPONSE" | jq '.'
echo ""

echo "⏳ Attendo 3 secondi per permettere l'indicizzazione..."
sleep 3
echo ""

echo "2️⃣  Test Query 1: Cosa sono i gatti?"
QUERY1=$(curl -s "http://localhost:8092/api/query?question=Cosa%20sono%20i%20gatti?")
echo "$QUERY1" | jq '.answer'
echo ""

echo "3️⃣  Test Query 2: Quante ore dormono i gatti?"
QUERY2=$(curl -s "http://localhost:8092/api/query?question=Quante%20ore%20dormono%20i%20gatti?")
echo "$QUERY2" | jq '.answer'
echo ""

echo "4️⃣  Test Query 3 (POST): Cosa mangiano i gatti?"
QUERY3=$(curl -s -X POST http://localhost:8092/api/query \
  -H "Content-Type: application/json" \
  -d '{"question": "Cosa mangiano i gatti?"}')
echo "$QUERY3" | jq '.answer'
echo ""

# Pulizia
rm -f test_document.txt

echo "✅ Test completati!"
echo ""
echo "💡 Prova ora con i tuoi documenti:"
echo "   curl -F 'file=@tuo_documento.pdf' http://localhost:8092/api/documents/upload"
