# RAG Client Web - Vaadin 24 + Spring Boot 3

Client web Vaadin 24 per il sistema RAG con interfaccia grafica completa.

## 🎯 Funzionalità

- **Query RAG**: Interfaccia per fare domande ai documenti indicizzati
- **Upload Documenti**: Carica nuovi documenti (PDF, Word, Excel, etc.)
- **Gestione Documenti**: Visualizza e cancella documenti indicizzati
- **System Status**: Monitora lo stato del backend

## 📋 Prerequisiti

1. **Backend RAG attivo**: Il sistema backend deve essere in esecuzione
2. **Java 17+**
3. **Maven 3.6+**

## 🚀 Quick Start

### 1. Avvia il Backend (se non già attivo)

```bash
cd /home/valyc-pc/lavoro/rag-langchain4j
./start.sh
```

Verifica che sia attivo su: http://localhost:8092

### 2. Avvia il Client Web

```bash
cd /home/valyc-pc/lavoro/rag-langchain4j/client-web
chmod +x start.sh
./start.sh
```

### 3. Accedi all'Applicazione

Apri il browser su: **http://localhost:8093**

## 📱 Interfaccia Utente

### 🔍 Query RAG (Home)
- Inserisci domande sui documenti
- Visualizza risposte con fonti
- Score di rilevanza per ogni fonte
- Scorciatoia: `Ctrl+Enter` per inviare

### 📤 Upload Document
- Drag & drop o click per selezionare file
- Supporta: PDF, Word, Excel, PowerPoint, TXT, HTML
- Max 100MB per file
- Feedback immediato su upload e processing

### 📚 Documents
- Lista completa documenti indicizzati
- Visualizza numero di chunks per documento
- Data di upload
- Cancellazione documenti con conferma
- Refresh automatico

### 💚 System Status
- Health check dei servizi backend
- Stato Document Processing API
- Stato Query API
- Stato generale del sistema
- Check manuale su richiesta

## ⚙️ Configurazione

Modifica `src/main/resources/application.yml`:

```yaml
server:
  port: 8093  # Porta dell'applicazione web

rag:
  api:
    base-url: http://localhost:8092  # URL del backend
    timeout: 60000  # Timeout richieste (ms)
```

## 🏗️ Architettura

```
client-web/
├── src/main/java/com/example/ragclient/
│   ├── RagClientApplication.java         # Main class
│   ├── config/
│   │   └── RestClientConfig.java         # WebClient configuration
│   ├── dto/
│   │   ├── QueryResponse.java            # Response models
│   │   ├── UploadResponse.java
│   │   ├── DocumentListResponse.java
│   │   ├── DocumentStatusResponse.java
│   │   └── HealthResponse.java
│   ├── service/
│   │   └── RagApiService.java            # REST client service
│   └── views/
│       ├── MainLayout.java               # Layout principale
│       ├── QueryView.java                # Vista query (/)
│       ├── UploadView.java               # Vista upload (/upload)
│       ├── DocumentListView.java         # Vista documenti (/documents)
│       └── StatusView.java               # Vista status (/status)
└── src/main/resources/
    └── application.yml                   # Configurazione
```

## 🔧 Sviluppo

### Build

```bash
mvn clean package
```

### Run in Dev Mode

```bash
mvn spring-boot:run
```

### Production Build

```bash
mvn clean package -Pproduction
java -jar target/rag-client-web-1.0.0.jar
```

## 🎨 Caratteristiche UI

- **Design moderno** con Vaadin Lumo theme
- **Responsive** - si adatta a desktop e mobile
- **Navigazione drawer** laterale con menu
- **Notifiche** per feedback utente
- **Loading indicators** durante operazioni
- **Conferme** per azioni critiche (es. cancellazione)
- **Icone** per migliore UX
- **Color coding** per stati (success, error, warning)

## 📡 API Backend Utilizzate

### Document API (`/api/documents`)
- `POST /upload` - Upload documento
- `GET /list` - Lista documenti
- `GET /status/{filename}` - Stato documento
- `GET /statuses` - Stati di tutti i documenti
- `DELETE /{filename}` - Cancella documento
- `GET /health` - Health check

### Query API (`/api/query`)
- `GET ?question=...` - Query GET
- `POST` - Query POST (JSON body)
- `GET /health` - Health check

## 🐛 Troubleshooting

### Backend non raggiungibile

```bash
# Verifica che il backend sia attivo
curl http://localhost:8092/api/documents/health

# Se non risponde, avvialo
cd /home/valyc-pc/lavoro/rag-langchain4j
./start.sh
```

### Porta 8093 già in uso

Modifica la porta in `application.yml`:

```yaml
server:
  port: 8094  # Usa un'altra porta
```

### Errore di build Maven

```bash
# Pulisci e ricompila
mvn clean install -U

# Salta i test se necessario
mvn clean package -DskipTests
```

### File troppo grande

Aumenta il limite in `application.yml`:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 200MB
      max-request-size: 200MB
```

## 🔐 Note di Sicurezza

⚠️ **ATTENZIONE**: Questa è una versione di sviluppo senza autenticazione.

Per produzione, aggiungi:
- Spring Security con autenticazione
- HTTPS/TLS
- CORS configuration
- Rate limiting
- Input validation aggiuntiva

## 📚 Tecnologie Utilizzate

- **Vaadin 24.3.0** - Framework UI
- **Spring Boot 3.2.0** - Backend framework
- **Spring WebFlux** - WebClient per REST calls
- **Lombok** - Riduzione boilerplate
- **Maven** - Build tool

## 📄 Licenza

Progetto educativo/di sviluppo.

## 🆘 Supporto

Per problemi:
1. Controlla che il backend sia attivo
2. Verifica i log dell'applicazione
3. Controlla la configurazione in `application.yml`
4. Testa le API backend direttamente con curl

## 🎉 Features Avanzate

- **Keyboard shortcuts**: Ctrl+Enter per inviare query
- **Auto-refresh**: Aggiornamento documenti con un click
- **Real-time feedback**: Notifiche per ogni operazione
- **Error handling**: Gestione errori user-friendly
- **Confirm dialogs**: Sicurezza per operazioni critiche
- **Responsive grid**: Tabelle adattive e ordinabili
