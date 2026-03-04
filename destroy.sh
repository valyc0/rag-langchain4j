#!/bin/bash

# ============================================================
# DESTROY SCRIPT - Ferma tutto e pulisce l'ambiente
# ============================================================
# Cosa fa:
#   1. Ferma i processi Spring Boot (backend + client-web)
#   2. Ferma e rimuove i container Docker (Qdrant + Ollama)
#   3. Rimuove i volumi Docker (dati Qdrant + modelli Ollama)
#   4. Pulisce le directory di lavoro (rag-input, rag-processed, rag-errors)
#   5. Pulisce le directory target Maven
#
# USO:
#   ./destroy.sh              → chiede conferma prima di ogni gruppo
#   ./destroy.sh --full       → distrugge anche i modelli Ollama scaricati
#   ./destroy.sh --yes        → skip tutte le conferme (ATTENZIONE)
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FULL_DESTROY=false
SKIP_CONFIRM=false

# Parse argomenti
for arg in "$@"; do
  case "$arg" in
    --full) FULL_DESTROY=true ;;
    --yes)  SKIP_CONFIRM=true ;;
  esac
done

RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
NC='\033[0m'

confirm() {
  if [ "$SKIP_CONFIRM" = true ]; then return 0; fi
  echo -e "${YELLOW}$1${NC}"
  read -r -p "Procedere? (y/N) " reply
  [[ "$reply" =~ ^[Yy]$ ]]
}

echo -e "${RED}"
echo "╔══════════════════════════════════════════════╗"
echo "║         ⚠️   DESTROY SCRIPT   ⚠️              ║"
echo "║  Ferma e pulisce tutto l'ambiente RAG        ║"
echo "╚══════════════════════════════════════════════╝"
echo -e "${NC}"

if [ "$FULL_DESTROY" = true ]; then
  echo -e "${RED}⚠️  Modalità --full: verranno eliminati anche i modelli Ollama scaricati${NC}"
fi
echo ""

# ── 1. PROCESSI SPRING BOOT ──────────────────────────────────────────────────

if confirm "1️⃣  Fermare i processi Spring Boot (backend :8092 + client-web :8093)?"; then
  echo -n "   🔴 Fermando processi mvn spring-boot:run... "
  PIDS=$(pgrep -f "spring-boot:run" 2>/dev/null || true)
  if [ -n "$PIDS" ]; then
    kill $PIDS 2>/dev/null || true
    sleep 2
    # Kill forzato se ancora in vita
    PIDS=$(pgrep -f "spring-boot:run" 2>/dev/null || true)
    [ -n "$PIDS" ] && kill -9 $PIDS 2>/dev/null || true
    echo -e "${GREEN}✅ Fermati${NC}"
  else
    echo -e "${CYAN}nessun processo attivo${NC}"
  fi

  echo -n "   🔴 Fermando eventuali processi java (app jar)... "
  PIDS=$(pgrep -f "rag-system\|rag-client" 2>/dev/null || true)
  if [ -n "$PIDS" ]; then
    kill $PIDS 2>/dev/null || true
    sleep 1
    echo -e "${GREEN}✅ Fermati${NC}"
  else
    echo -e "${CYAN}nessuno${NC}"
  fi
else
  echo "   ⏭️  Saltato"
fi

echo ""

# ── 2. CONTAINER DOCKER ──────────────────────────────────────────────────────

if confirm "2️⃣  Fermare e rimuovere i container Docker (Qdrant + Ollama)?"; then
  echo "   🔴 Fermo Qdrant..."
  cd "$SCRIPT_DIR"
  docker compose down 2>/dev/null && echo -e "   ${GREEN}✅ Qdrant fermato${NC}" || echo -e "   ${CYAN}(già fermo)${NC}"

  echo "   🔴 Fermo Ollama..."
  cd "$SCRIPT_DIR/ollama"
  docker compose down 2>/dev/null && echo -e "   ${GREEN}✅ Ollama fermato${NC}" || echo -e "   ${CYAN}(già fermo)${NC}"
  cd "$SCRIPT_DIR"
else
  echo "   ⏭️  Saltato"
fi

echo ""

# ── 3. DATI QDRANT ───────────────────────────────────────────────────────────

if confirm "3️⃣  Eliminare i dati Qdrant (qdrant_storage/ + volume Docker)?${NC}${RED}\n   ⚠️  Tutti i documenti indicizzati verranno persi!${NC}"; then
  echo -n "   🗑️  Rimuovendo volume Docker qdrant_storage... "
  docker volume rm rag-langchain4j_qdrant_storage 2>/dev/null \
    && echo -e "${GREEN}✅${NC}" || echo -e "${CYAN}(non trovato o già rimosso)${NC}"

  echo -n "   🗑️  Rimuovendo directory qdrant_storage/... "
  rm -rf "$SCRIPT_DIR/qdrant_storage"
  echo -e "${GREEN}✅${NC}"
else
  echo "   ⏭️  Saltato — dati Qdrant mantenuti"
fi

echo ""

# ── 4. MODELLI OLLAMA (solo con --full) ──────────────────────────────────────

if [ "$FULL_DESTROY" = true ]; then
  if confirm "4️⃣  [--full] Eliminare il volume Ollama (modelli LLM + embedding scaricati)?\n   ⚠️  Dovrai riscaricare llama3.2:1b (~1.3GB) e nomic-embed-text (~274MB)"; then
    echo -n "   🗑️  Rimuovendo volume Docker ollama_data... "
    docker volume rm ollama_ollama_data 2>/dev/null \
      && echo -e "${GREEN}✅${NC}" || echo -e "${CYAN}(non trovato o già rimosso)${NC}"
  else
    echo "   ⏭️  Saltato — modelli Ollama mantenuti"
  fi
  echo ""
fi

# ── 5. DIRECTORY DI LAVORO RAG ───────────────────────────────────────────────

if confirm "5️⃣  Pulire le directory di lavoro (rag-input/, rag-processed/, rag-errors/)?"; then
  for dir in rag-input rag-processed rag-errors; do
    if [ -d "$SCRIPT_DIR/$dir" ]; then
      COUNT=$(find "$SCRIPT_DIR/$dir" -type f | wc -l)
      rm -rf "${SCRIPT_DIR:?}/$dir/"*
      echo -e "   🗑️  $dir/ — rimossi $COUNT file  ${GREEN}✅${NC}"
    else
      echo -e "   ${CYAN}$dir/ non esiste — saltato${NC}"
    fi
  done
else
  echo "   ⏭️  Saltato"
fi

echo ""

# ── 6. TARGET MAVEN ──────────────────────────────────────────────────────────

if confirm "6️⃣  Pulire le directory target/ Maven (backend + client-web)?"; then
  echo -n "   🗑️  mvn clean (backend)... "
  cd "$SCRIPT_DIR" && mvn clean -q 2>/dev/null && echo -e "${GREEN}✅${NC}" || echo -e "${YELLOW}⚠️ errore${NC}"

  echo -n "   🗑️  mvn clean (client-web)... "
  cd "$SCRIPT_DIR/client-web" && mvn clean -q 2>/dev/null && echo -e "${GREEN}✅${NC}" || echo -e "${YELLOW}⚠️ errore${NC}"
  cd "$SCRIPT_DIR"
else
  echo "   ⏭️  Saltato"
fi

echo ""

# ── 7. LOG TEMPORANEI ────────────────────────────────────────────────────────

if confirm "7️⃣  Eliminare i log temporanei (/tmp/app.log, /tmp/client-web.log)?"; then
  rm -f /tmp/app.log /tmp/client-web.log
  echo -e "   🗑️  Log rimossi  ${GREEN}✅${NC}"
else
  echo "   ⏭️  Saltato"
fi

echo ""

# ── RIEPILOGO ────────────────────────────────────────────────────────────────

echo -e "${GREEN}"
echo "╔══════════════════════════════════════════════╗"
echo "║            ✅  Destroy completato            ║"
echo "╚══════════════════════════════════════════════╝"
echo -e "${NC}"
echo "Per ripartire da zero:"
echo "  ./start.sh                                    # profilo Gemini"
echo "  mvn spring-boot:run -Dspring-boot.run.profiles=ollama  # profilo Ollama"
echo ""
