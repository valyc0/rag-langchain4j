#!/bin/bash

# RAG Client Web - Vaadin 24 + Spring Boot 3
# Avvia il client web sulla porta 8093

cd "$(dirname "$0")"

echo "🚀 Avvio RAG Client Web..."
echo "📍 URL: http://localhost:8093"
echo ""

mvn spring-boot:run
