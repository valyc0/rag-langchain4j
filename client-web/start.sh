#!/bin/bash

# RAG Client Web - Startup Script

echo "🚀 Starting RAG Client Web Application..."
echo ""

# Check if backend is running
echo "🔍 Checking backend availability..."
if curl -s http://localhost:8092/api/documents/health > /dev/null 2>&1; then
    echo "✅ Backend is running on http://localhost:8092"
else
    echo "⚠️  WARNING: Backend appears to be down!"
    echo "   Make sure to start the RAG backend first:"
    echo "   cd /home/valyc-pc/lavoro/rag-langchain4j && ./start.sh"
    echo ""
    read -p "Continue anyway? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

echo ""
echo "🎯 Starting Vaadin application with Spring Boot..."
echo ""
echo "📍 Application will be available at: http://localhost:8093"
echo ""

mvn spring-boot:run
