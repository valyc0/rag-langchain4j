#!/bin/bash

echo "🚀 Avvio Sistema RAG"
echo ""

# 1. Avvia Qdrant
echo "1️⃣  Avvio Qdrant..."
docker compose up -d

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
echo "3️⃣  Configurazione LLM Provider..."
echo ""

# Determina il provider (default: gemini)
PROVIDER=${LLM_PROVIDER:-gemini}
echo "🤖 Provider LLM: $PROVIDER"
echo ""

# Configurazione basata sul provider
case "$PROVIDER" in
    gemini)
        if [ -z "$GEMINI_API_KEY" ]; then
            echo "ℹ️  La GEMINI_API_KEY non è impostata."
            echo "   Puoi ottenerla da: https://aistudio.google.com/app/apikey"
            echo ""
            echo "   Per impostarla permanentemente, aggiungi al tuo ~/.bashrc o ~/.zshrc:"
            echo "   export GEMINI_API_KEY=\"your_api_key_here\""
            echo ""
            read -p "🔑 Inserisci la tua Gemini API Key (o premi Ctrl+C per annullare): " GEMINI_API_KEY
            
            if [ -z "$GEMINI_API_KEY" ]; then
                echo "❌ API Key non inserita. Uscita."
                exit 1
            fi
            
            export GEMINI_API_KEY
            echo "✅ Gemini API Key impostata per questa sessione."
        else
            echo "✅ Gemini API Key già configurata."
        fi
        ;;
        
    ollama)
        echo "🦙 Verifica configurazione Ollama..."
        
        # Verifica se Ollama è in esecuzione
        if ! curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
            echo "⚠️  Ollama non sembra in esecuzione su http://localhost:11434"
            echo ""
            echo "   Per installare Ollama:"
            echo "   curl -fsSL https://ollama.ai/install.sh | sh"
            echo ""
            echo "   Per avviare Ollama:"
            echo "   ollama serve"
            echo ""
            read -p "❓ Vuoi continuare comunque? (y/N): " CONTINUE
            if [[ ! "$CONTINUE" =~ ^[Yy]$ ]]; then
                exit 1
            fi
        else
            echo "✅ Ollama in esecuzione."
            
            # Mostra modelli disponibili
            MODELS=$(curl -s http://localhost:11434/api/tags | grep -o '"name":"[^"]*"' | cut -d'"' -f4)
            if [ -n "$MODELS" ]; then
                echo "📦 Modelli disponibili:"
                echo "$MODELS" | sed 's/^/   - /'
            fi
            
            # Imposta modello di default se non specificato
            if [ -z "$OLLAMA_MODEL" ]; then
                export OLLAMA_MODEL="llama3.2"
                echo ""
                echo "🔧 Modello non specificato, uso default: $OLLAMA_MODEL"
            else
                echo ""
                echo "🔧 Modello configurato: $OLLAMA_MODEL"
            fi
        fi
        ;;
        
    openrouter)
        if [ -z "$OPENROUTER_API_KEY" ]; then
            echo "ℹ️  La OPENROUTER_API_KEY non è impostata."
            echo "   Puoi ottenerla da: https://openrouter.ai/keys"
            echo ""
            echo "   Per impostarla permanentemente, aggiungi al tuo ~/.bashrc o ~/.zshrc:"
            echo "   export OPENROUTER_API_KEY=\"your_api_key_here\""
            echo ""
            read -p "🔑 Inserisci la tua OpenRouter API Key (o premi Ctrl+C per annullare): " OPENROUTER_API_KEY
            
            if [ -z "$OPENROUTER_API_KEY" ]; then
                echo "❌ API Key non inserita. Uscita."
                exit 1
            fi
            
            export OPENROUTER_API_KEY
            echo "✅ OpenRouter API Key impostata per questa sessione."
        else
            echo "✅ OpenRouter API Key già configurata."
        fi
        
        # Imposta modello di default se non specificato
        if [ -z "$OPENROUTER_MODEL" ]; then
            export OPENROUTER_MODEL="anthropic/claude-3-haiku"
            echo "🔧 Modello non specificato, uso default: $OPENROUTER_MODEL"
        else
            echo "🔧 Modello configurato: $OPENROUTER_MODEL"
        fi
        ;;
        
    *)
        echo "⚠️  Provider '$PROVIDER' non riconosciuto."
        echo "    Provider supportati: gemini, ollama, openrouter"
        echo "    Uso default: gemini"
        export LLM_PROVIDER="gemini"
        ;;
esac

echo ""
echo "4️⃣  Avvio Spring Boot..."
echo ""

# Avvia Spring Boot
mvn spring-boot:run
