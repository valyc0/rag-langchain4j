# RAG Client Web - Vaadin 24 + Spring Boot 3

Client web per interagire con il sistema RAG.

## Requisiti

- Java 17+
- Maven 3.6+
- Backend RAG attivo su `http://localhost:8092`

## Avvio

```bash
chmod +x start.sh
./start.sh
```

Oppure direttamente:

```bash
mvn spring-boot:run
```

Il client sarà disponibile su: **http://localhost:8093**

## Funzionalità

- 💬 **Chat RAG**: Interfaccia chat per fare domande sui documenti
- 📄 **Documenti**: Visualizza documenti indicizzati
- 📤 **Upload**: Carica nuovi documenti
- 🔧 **Status**: Verifica stato del sistema

## Configurazione

Modifica `src/main/resources/application.yml`:

```yaml
rag:
  api:
    base-url: http://localhost:8092  # URL backend RAG
    timeout-seconds: 60              # Timeout chiamate API
```
