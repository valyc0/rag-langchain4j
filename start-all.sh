#!/bin/bash

# ============================================================
# START-ALL — Avvia l'intero stack RAG
# ============================================================
# Avvia in sequenza:
#   1. Qdrant       (Docker, :6333/:6334)
#   2. Ollama       (Docker, :11434)
#   3. Backend RAG  (Spring Boot, :8092) — profilo ollama
#   4. Client Web   (Vaadin, :8093)
#
# USO:
#   ./start-all.sh             → tutto locale con profilo ollama
#   ./start-all.sh --no-client → solo backend (niente client-web)
#   ./start-all.sh --gemini    → usa Gemini come LLM (richiede GEMINI_API_KEY)
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SPRING_PROFILE="ollama"
START_CLIENT=true

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

for arg in "$@"; do
  case "$arg" in
    --no-client) START_CLIENT=false ;;
    --gemini)    SPRING_PROFILE="gemini" ;;
  esac
done

echo -e "${GREEN}"
echo "╔══════════════════════════════════════════════╗"
echo "║         🚀  Avvio Stack RAG Completo         ║"
echo "╚══════════════════════════════════════════════╝"
echo -e "${NC}"
echo "  Profilo Spring : $SPRING_PROFILE"
echo "  Client Web     : $( [ "$START_CLIENT" = true ] && echo "sì (:8093)" || echo "no" )"
echo ""

# ── 1. QDRANT ────────────────────────────────────────────────────────────────

echo "1️⃣  Avvio Qdrant..."
cd "$SCRIPT_DIR"
docker compose up -d

echo -n "   ⏳ Attendo che Qdrant sia pronto..."
for i in $(seq 1 30); do
  if curl -s http://localhost:6333/ > /dev/null 2>&1; then
    echo -e " ${GREEN}✅ pronto (${i}s)${NC}"
    break
  fi
  sleep 1
  echo -n "."
  if [ "$i" -eq 30 ]; then
    echo -e " ${RED}❌ timeout — verifica: docker compose logs qdrant${NC}"
    exit 1
  fi
done

# ── 2. OLLAMA ────────────────────────────────────────────────────────────────

echo ""
echo "2️⃣  Avvio Ollama..."
cd "$SCRIPT_DIR/ollama"
docker compose up -d
cd "$SCRIPT_DIR"

echo -n "   ⏳ Attendo che Ollama sia pronto..."
for i in $(seq 1 30); do
  if curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo -e " ${GREEN}✅ pronto (${i}s)${NC}"
    break
  fi
  sleep 1
  echo -n "."
  if [ "$i" -eq 30 ]; then
    echo -e " ${RED}❌ timeout — verifica: docker logs ollama${NC}"
    exit 1
  fi
done

# Verifica modelli necessari per il profilo ollama
if [ "$SPRING_PROFILE" = "ollama" ]; then
  echo ""
  echo "   🔍 Verifico modelli Ollama..."

  LLM_MODEL="${OLLAMA_MODEL:-llama3.2:1b}"
  EMBED_MODEL="${EMBEDDING_OLLAMA_MODEL:-nomic-embed-text}"

  for MODEL in "$LLM_MODEL" "$EMBED_MODEL"; do
    if docker exec ollama ollama list 2>/dev/null | grep -q "^${MODEL}"; then
      echo -e "   ✅ $MODEL già installato"
    else
      echo -e "   ${YELLOW}⬇️  Scarico $MODEL (potrebbe richiedere qualche minuto)...${NC}"
      docker exec ollama ollama pull "$MODEL"
      echo -e "   ${GREEN}✅ $MODEL scaricato${NC}"
    fi
  done
fi

# ── 3. GEMINI API KEY (solo se profilo gemini) ───────────────────────────────

if [ "$SPRING_PROFILE" = "gemini" ]; then
  echo ""
  if [ -z "$GEMINI_API_KEY" ]; then
    echo -e "${YELLOW}   🔑 GEMINI_API_KEY non impostata.${NC}"
    read -r -p "   Inserisci la Gemini API Key (o premi Ctrl+C per annullare): " GEMINI_API_KEY
    [ -z "$GEMINI_API_KEY" ] && echo "❌ API Key non inserita." && exit 1
    export GEMINI_API_KEY
  else
    echo -e "   ${GREEN}✅ Gemini API Key configurata${NC}"
  fi
fi

# ── 4. BACKEND SPRING BOOT ───────────────────────────────────────────────────

echo ""
echo "3️⃣  Avvio Backend RAG (profilo: $SPRING_PROFILE)..."
cd "$SCRIPT_DIR"

if [ "$SPRING_PROFILE" = "ollama" ]; then
  mvn spring-boot:run -Dspring-boot.run.profiles=ollama > /tmp/app.log 2>&1 &
else
  mvn spring-boot:run > /tmp/app.log 2>&1 &
fi
BACKEND_PID=$!
echo "   PID: $BACKEND_PID — log: /tmp/app.log"

echo -n "   ⏳ Attendo che il backend sia pronto..."
for i in $(seq 1 60); do
  if curl -s http://localhost:8092/api/documents/health > /dev/null 2>&1; then
    echo -e " ${GREEN}✅ pronto (${i}s)${NC}"
    break
  fi
  sleep 3
  echo -n "."
  if [ "$i" -eq 60 ]; then
    echo -e " ${RED}❌ timeout — controlla /tmp/app.log${NC}"
    tail -20 /tmp/app.log
    exit 1
  fi
done

# ── 5. CLIENT WEB ────────────────────────────────────────────────────────────

if [ "$START_CLIENT" = true ]; then
  echo ""
  echo "4️⃣  Avvio Client Web (Vaadin :8093)..."
  cd "$SCRIPT_DIR/client-web"
  mvn spring-boot:run > /tmp/client-web.log 2>&1 &
  CLIENT_PID=$!
  echo "   PID: $CLIENT_PID — log: /tmp/client-web.log"

  echo -n "   ⏳ Attendo che il client web sia pronto..."
  for i in $(seq 1 40); do
    if curl -s http://localhost:8093 > /dev/null 2>&1; then
      echo -e " ${GREEN}✅ pronto (${i}s)${NC}"
      break
    fi
    sleep 3
    echo -n "."
    if [ "$i" -eq 40 ]; then
      echo -e " ${YELLOW}⚠️  non risponde ancora — controlla /tmp/client-web.log${NC}"
    fi
  done
  cd "$SCRIPT_DIR"
fi

# ── RIEPILOGO ────────────────────────────────────────────────────────────────

echo ""
echo -e "${GREEN}"
echo "╔══════════════════════════════════════════════╗"
echo "║          ✅  Stack RAG avviato!              ║"
echo "╚══════════════════════════════════════════════╝"
echo -e "${NC}"
echo "  🗄️  Qdrant dashboard  : http://localhost:6333/dashboard"
echo "  🔌 Backend API        : http://localhost:8092"
echo "  🖥️  Client Web        : http://localhost:8093"
echo "  🦙 Ollama             : http://localhost:11434"
echo ""
echo "  📄 Log backend        : tail -f /tmp/app.log"
echo "  📄 Log client         : tail -f /tmp/client-web.log"
echo ""
echo "  ⛔ Per fermare tutto  : ./destroy.sh"
echo ""
