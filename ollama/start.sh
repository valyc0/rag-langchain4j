#!/bin/bash

echo "🦙 Avvio Ollama con modello llama3.2:1b"
echo ""

# Avvia Ollama
docker-compose up -d ollama

echo ""
echo "⏳ Attendo che Ollama sia pronto (max 30 secondi)..."
COUNTER=0
until curl -s http://localhost:11434/api/tags > /dev/null 2>&1 || [ $COUNTER -eq 30 ]; do
    echo -n "."
    sleep 1
    COUNTER=$((COUNTER+1))
done
echo ""

if [ $COUNTER -eq 30 ]; then
    echo "❌ Timeout: Ollama non risponde dopo 30 secondi"
    echo "   Controlla i log con: docker-compose logs ollama"
    exit 1
fi

echo "✅ Ollama è pronto!"
echo ""

# Scarica il modello se non esiste
echo "📥 Verifica e download modello llama3.2:1b..."
docker-compose run --rm ollama-setup

echo ""
echo "✅ Setup completato!"
echo ""
echo "📊 Modelli disponibili:"
docker exec ollama ollama list
echo ""
echo "🔗 Ollama è in esecuzione su: http://localhost:11434"
echo ""
echo "Comandi utili:"
echo "  - Verifica stato: docker-compose ps"
echo "  - Log: docker-compose logs -f ollama"
echo "  - Stop: docker-compose down"
echo "  - Testa modello: docker exec -it ollama ollama run llama3.2:1b"
