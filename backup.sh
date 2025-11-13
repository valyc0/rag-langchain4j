#!/bin/bash

# Script di backup del progetto RAG
# Esclude: qdrant_storage, target
# Output: ../rag-backup-YYYYMMDD-HHMMSS.tar.gz

# Genera il nome del file con timestamp
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_NAME="rag-backup-${TIMESTAMP}.tar.gz"
BACKUP_PATH="../${BACKUP_NAME}"

echo "📦 Inizio backup progetto RAG"
echo "📁 File di destinazione: ${BACKUP_PATH}"
echo ""

# Crea l'archivio escludendo le directory specificate
tar -czf "${BACKUP_PATH}" \
  --exclude='qdrant_storage' \
  --exclude='target' \
  --exclude='*.log' \
  --exclude='.git' \
  .

# Verifica il risultato
if [ $? -eq 0 ]; then
    BACKUP_SIZE=$(du -h "${BACKUP_PATH}" | cut -f1)
    echo "✅ Backup completato con successo!"
    echo "📊 Dimensione: ${BACKUP_SIZE}"
    echo "📂 Percorso: ${BACKUP_PATH}"
else
    echo "❌ Errore durante il backup!"
    exit 1
fi
