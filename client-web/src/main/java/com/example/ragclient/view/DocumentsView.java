package com.example.ragclient.view;

import com.example.ragclient.model.DocumentListResponse;
import com.example.ragclient.model.DeleteResponse;
import com.example.ragclient.model.UploadResponse;
import com.example.ragclient.model.DocumentStatusResponse;
import com.example.ragclient.service.RagApiService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * View per upload e visualizzazione documenti indicizzati
 */
@Route(value = "documents", layout = MainLayout.class)
@PageTitle("Documenti")
@Slf4j
public class DocumentsView extends VerticalLayout {

    private final RagApiService ragApiService;
    private final Grid<DocumentInfo> grid;
    private final Span totalDocsSpan;
    private final Span totalChunksSpan;
    private ProgressBar progressBar;
    private Div resultDiv;
    private Upload upload;
    private ScheduledExecutorService pollingScheduler;
    private boolean isPolling = false;

    public DocumentsView(RagApiService ragApiService) {
        this.ragApiService = ragApiService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        // Header
        H2 title = new H2("📄 Gestione Documenti");
        title.addClassName(LumoUtility.Margin.NONE);

        // Upload section
        VerticalLayout uploadSection = createUploadSection();
        uploadSection.getStyle()
                .set("padding", "var(--lumo-space-m)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("margin-bottom", "var(--lumo-space-l)");

        // Documents list header
        H3 listTitle = new H3("📚 Documenti Indicizzati");
        listTitle.addClassName(LumoUtility.Margin.Top.MEDIUM);

        // Stats
        totalDocsSpan = new Span("📚 Documenti: -");
        totalDocsSpan.getStyle().set("font-weight", "bold");
        
        totalChunksSpan = new Span("📊 Chunks totali: -");
        totalChunksSpan.getStyle().set("font-weight", "bold");

        Button refreshButton = new Button("Aggiorna", VaadinIcon.REFRESH.create());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        refreshButton.addClickListener(e -> loadDocuments());

        HorizontalLayout statsLayout = new HorizontalLayout(totalDocsSpan, totalChunksSpan, refreshButton);
        statsLayout.setAlignItems(Alignment.CENTER);
        statsLayout.setSpacing(true);

        // Grid
        grid = new Grid<>(DocumentInfo.class, false);
        grid.addColumn(DocumentInfo::getFilename).setHeader("📄 Nome File").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(DocumentInfo::getChunks).setHeader("📊 Chunks").setAutoWidth(true);
        grid.addColumn(DocumentInfo::getStatusLabel).setHeader("🔄 Stato").setAutoWidth(true);
        grid.addColumn(DocumentInfo::getFormattedDate).setHeader("📅 Data").setAutoWidth(true);
        
        // Colonna azioni
        grid.addComponentColumn(documentInfo -> {
            Button deleteButton = new Button("Elimina", VaadinIcon.TRASH.create());
            deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            deleteButton.addClickListener(e -> confirmDelete(documentInfo));
            deleteButton.setEnabled("READY".equals(documentInfo.getStatus())); // Abilita solo se READY
            return deleteButton;
        }).setHeader("Azioni").setAutoWidth(true);
        
        grid.setSizeFull();

        add(title, uploadSection, listTitle, statsLayout, grid);
        setFlexGrow(1, grid);

        // Carica dati
        loadDocuments();
    }

    private VerticalLayout createUploadSection() {
        VerticalLayout uploadLayout = new VerticalLayout();
        uploadLayout.setSpacing(true);
        uploadLayout.setPadding(false);

        H3 uploadTitle = new H3("📤 Upload Nuovo Documento");
        uploadTitle.addClassName(LumoUtility.Margin.NONE);

        // Istruzioni
        Paragraph instructions = new Paragraph(
                "Carica documenti per indicizzarli nel sistema RAG. " +
                "Formati supportati: PDF, Word, Excel, PowerPoint, TXT, HTML, XML"
        );
        instructions.getStyle().set("color", "var(--lumo-secondary-text-color)");

        // Upload component
        MemoryBuffer buffer = new MemoryBuffer();
        upload = new Upload(buffer);
        upload.setAcceptedFileTypes(
                ".pdf", ".doc", ".docx", 
                ".xls", ".xlsx", 
                ".ppt", ".pptx",
                ".txt", ".html", ".xml"
        );
        upload.setMaxFileSize(100 * 1024 * 1024); // 100MB
        upload.setDropLabel(new Span("Trascina qui il file o clicca per selezionare"));
        upload.setUploadButton(null);
        upload.setAutoUpload(true);

        // Progress bar
        progressBar = new ProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setWidth("100%");

        // Result div
        resultDiv = new Div();
        resultDiv.getStyle()
                .set("padding", "var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("margin-top", "var(--lumo-space-m)");
        resultDiv.setVisible(false);

        // Upload handlers
        upload.addStartedListener(event -> {
            progressBar.setVisible(true);
            resultDiv.setVisible(false);
        });

        upload.addSucceededListener(event -> {
            String filename = event.getFileName();
            try {
                byte[] content = buffer.getInputStream().readAllBytes();
                
                // Sgancia IMMEDIATAMENTE - mostra "done"
                progressBar.setVisible(false);
                upload.clearFileList();
                
                Notification.show("✅ Upload completato! Documento in elaborazione...", 
                        3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                
                // Avvia subito il polling
                startPolling();
                
                // Upload al backend in background (non blocca l'UI)
                new Thread(() -> {
                    try {
                        UploadResponse response = ragApiService.uploadDocument(filename, content);
                        
                        getUI().ifPresent(ui -> ui.access(() -> {
                            // Mostra solo il risultato dettagliato nel pannello
                            showResult(response);
                            
                            // Se c'è un errore, mostra notifica
                            if (response.getMessage() != null && response.getMessage().contains("❌")) {
                                Notification.show(response.getMessage(), 
                                        5000, Notification.Position.BOTTOM_CENTER)
                                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                            }
                        }));
                    } catch (Exception e) {
                        getUI().ifPresent(ui -> ui.access(() -> {
                            Notification.show("❌ Errore durante l'upload: " + e.getMessage(), 
                                    5000, Notification.Position.BOTTOM_CENTER)
                                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                            log.error("Errore upload in background", e);
                        }));
                    }
                }).start();
                
            } catch (IOException e) {
                progressBar.setVisible(false);
                Notification.show("❌ Errore lettura file: " + e.getMessage(), 
                        5000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        upload.addFailedListener(event -> {
            progressBar.setVisible(false);
            upload.clearFileList();
            Notification.show("❌ Upload fallito: " + event.getReason().getMessage(), 
                    5000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        });

        uploadLayout.add(uploadTitle, instructions, upload, progressBar, resultDiv);
        return uploadLayout;
    }

    private void showResult(UploadResponse response) {
        resultDiv.removeAll();
        resultDiv.setVisible(true);

        // Gestione caso response null
        if (response == null) {
            resultDiv.getStyle()
                    .set("background", "var(--lumo-error-color-10pct)")
                    .set("border", "1px solid var(--lumo-error-color)");
            
            Span errorIcon = new Span("❌ Errore");
            errorIcon.getStyle()
                    .set("font-weight", "bold")
                    .set("color", "var(--lumo-error-text-color)")
                    .set("display", "block");
            
            Paragraph errorMsg = new Paragraph("Errore del server durante il processamento del documento");
            errorMsg.getStyle()
                    .set("margin", "var(--lumo-space-s) 0")
                    .set("font-weight", "bold");
            
            resultDiv.add(errorIcon, errorMsg);
            return;
        }

        if (response.getMessage() != null && response.getMessage().contains("✅")) {
            resultDiv.getStyle()
                    .set("background", "var(--lumo-success-color-10pct)")
                    .set("border", "1px solid var(--lumo-success-color)");

            Span successIcon = new Span("✅ Upload completato!");
            successIcon.getStyle()
                    .set("font-weight", "bold")
                    .set("color", "var(--lumo-success-text-color)")
                    .set("display", "block")
                    .set("margin-bottom", "var(--lumo-space-s)");
            resultDiv.add(successIcon);

            if (response.getData() != null) {
                addResultLine(resultDiv, "📄 File", response.getData().getFilename());
                addResultLine(resultDiv, "📊 Chunks creati", String.valueOf(response.getData().getChunksCreated()));
                addResultLine(resultDiv, "📝 Lunghezza testo", response.getData().getTextLength() + " caratteri");
                addResultLine(resultDiv, "💾 Dimensione", formatBytes(response.getData().getSizeBytes()));
            }

            Notification.show("✅ Documento caricato con successo!", 
                    3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } else {
            resultDiv.getStyle()
                    .set("background", "var(--lumo-error-color-10pct)")
                    .set("border", "1px solid var(--lumo-error-color)");

            Span errorIcon = new Span("❌ Errore");
            errorIcon.getStyle()
                    .set("font-weight", "bold")
                    .set("color", "var(--lumo-error-text-color)")
                    .set("display", "block");
            
            // Mostra il messaggio principale (o un fallback se vuoto)
            String message = response.getMessage() != null && !response.getMessage().isEmpty() 
                    ? response.getMessage() 
                    : "Errore durante l'upload del documento";
            Paragraph errorMsg = new Paragraph(message);
            errorMsg.getStyle()
                    .set("margin", "var(--lumo-space-s) 0")
                    .set("font-weight", "bold");
            
            resultDiv.add(errorIcon, errorMsg);
            
            // Se c'è un dettaglio dell'errore, mostralo
            if (response.getError() != null && !response.getError().isEmpty()) {
                Paragraph errorDetail = new Paragraph(response.getError());
                errorDetail.getStyle()
                        .set("margin", "var(--lumo-space-s) 0 0 0")
                        .set("color", "var(--lumo-secondary-text-color)")
                        .set("font-size", "var(--lumo-font-size-s)");
                resultDiv.add(errorDetail);
            }
            
            // Mostra anche una notifica
            Notification.show(message, 
                    5000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void addResultLine(Div parent, String label, String value) {
        Div line = new Div();
        line.getStyle().set("margin", "var(--lumo-space-xs) 0");
        
        Span labelSpan = new Span(label + ": ");
        labelSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");
        
        Span valueSpan = new Span(value);
        valueSpan.getStyle().set("font-weight", "500");
        
        line.add(labelSpan, valueSpan);
        parent.add(line);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void loadDocuments() {
        try {
            // Prima carica i documenti da Qdrant (già indicizzati)
            DocumentListResponse listResponse = ragApiService.getDocumentsList();
            
            // Poi ottiene gli stati in tempo reale
            Map<String, Object> allStatuses = ragApiService.getAllStatuses();
            
            List<DocumentInfo> documents = new ArrayList<>();
            int totalChunks = 0;
            int totalDocs = 0;
            boolean hasProcessing = false;
            
            // Crea una mappa combinata di tutti i documenti
            Map<String, DocumentInfo> documentsMap = new HashMap<>();
            
            // 1. Aggiungi documenti da Qdrant (READY per default)
            if (listResponse != null && listResponse.getDocuments() != null) {
                for (Map.Entry<String, Integer> entry : listResponse.getDocuments().entrySet()) {
                    String filename = entry.getKey();
                    int chunks = entry.getValue();
                    Long timestamp = listResponse.getTimestamps() != null 
                            ? listResponse.getTimestamps().get(filename) 
                            : null;
                    
                    DocumentInfo doc = new DocumentInfo(filename, chunks, timestamp);
                    doc.setStatus("READY");
                    documentsMap.put(filename, doc);
                }
            }
            
            // 2. Sovrascrivi/aggiungi con gli stati in tempo reale (PROCESSING, ERROR)
            if (allStatuses != null && !allStatuses.isEmpty()) {
                for (Map.Entry<String, Object> entry : allStatuses.entrySet()) {
                    String filename = entry.getKey();
                    Map<String, Object> statusInfo = (Map<String, Object>) entry.getValue();
                    
                    String status = (String) statusInfo.get("status");
                    Integer chunks = statusInfo.get("chunks") != null ? 
                            ((Number) statusInfo.get("chunks")).intValue() : 0;
                    Long uploadTimestamp = statusInfo.get("uploadTimestamp") != null ?
                            ((Number) statusInfo.get("uploadTimestamp")).longValue() : null;
                    
                    DocumentInfo doc = documentsMap.get(filename);
                    if (doc == null) {
                        // Nuovo documento non ancora in Qdrant
                        doc = new DocumentInfo(filename, chunks, uploadTimestamp);
                        documentsMap.put(filename, doc);
                    }
                    
                    // Aggiorna lo stato
                    doc.setStatus(status);
                    if (chunks > 0 && doc.getChunks() == 0) {
                        doc.setChunks(chunks);
                    }
                    
                    if ("PROCESSING".equals(status)) {
                        hasProcessing = true;
                    }
                }
            }
            
            // Converti la mappa in lista e calcola statistiche
            for (DocumentInfo doc : documentsMap.values()) {
                documents.add(doc);
                if ("READY".equals(doc.getStatus())) {
                    totalChunks += doc.getChunks();
                    totalDocs++;
                }
            }
            
            totalDocsSpan.setText("📚 Documenti pronti: " + totalDocs);
            totalChunksSpan.setText("📊 Chunks totali: " + totalChunks);
            
            grid.setItems(documents);
            
            // Se ci sono documenti in PROCESSING, continua il polling
            if (hasProcessing) {
                startPolling();
            } else {
                stopPolling();
            }
            
        } catch (Exception e) {
            log.error("Errore nel caricamento documenti", e);
            Notification.show("❌ Errore nel caricamento documenti: " + e.getMessage(), 
                    5000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
    
    /**
     * Avvia il polling per aggiornare lo stato dei documenti
     */
    private void startPolling() {
        if (isPolling) {
            return; // Polling già attivo
        }
        
        isPolling = true;
        pollingScheduler = Executors.newSingleThreadScheduledExecutor();
        
        pollingScheduler.scheduleAtFixedRate(() -> {
            getUI().ifPresent(ui -> ui.access(() -> {
                loadDocuments();
            }));
        }, 2, 2, TimeUnit.SECONDS); // Polling ogni 2 secondi
        
        log.info("🔄 Polling avviato per aggiornare stato documenti");
    }
    
    /**
     * Ferma il polling
     */
    private void stopPolling() {
        if (!isPolling) {
            return;
        }
        
        isPolling = false;
        if (pollingScheduler != null && !pollingScheduler.isShutdown()) {
            pollingScheduler.shutdown();
            log.info("⏸️ Polling fermato");
        }
    }
    
    @Override
    protected void onDetach(com.vaadin.flow.component.DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        stopPolling();
    }

    private void confirmDelete(DocumentInfo documentInfo) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Conferma Cancellazione");
        dialog.setText("Vuoi eliminare il documento \"" + documentInfo.getFilename() + "\"? " +
                "Verranno rimossi " + documentInfo.getChunks() + " chunks.");
        
        dialog.setCancelable(true);
        dialog.setCancelText("Annulla");
        
        dialog.setConfirmText("Elimina");
        dialog.setConfirmButtonTheme("error primary");
        
        dialog.addConfirmListener(e -> deleteDocument(documentInfo));
        
        dialog.open();
    }

    private void deleteDocument(DocumentInfo documentInfo) {
        try {
            DeleteResponse response = ragApiService.deleteDocument(documentInfo.getFilename());
            
            if ("success".equals(response.getStatus())) {
                Notification.show("✅ " + response.getMessage() + " (" + 
                        response.getChunksDeleted() + " chunks rimossi)", 
                        5000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                loadDocuments(); // Ricarica la lista
            } else if ("not_found".equals(response.getStatus())) {
                Notification.show("⚠️ " + response.getMessage(), 
                        5000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_CONTRAST);
                loadDocuments(); // Ricarica comunque
            } else {
                Notification.show("❌ " + response.getMessage(), 
                        5000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        } catch (Exception e) {
            Notification.show("❌ Errore durante la cancellazione: " + e.getMessage(), 
                    5000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    /**
     * DTO per le informazioni documento
     */
    public static class DocumentInfo {
        private final String filename;
        private int chunks;
        private final Long timestamp;
        private String status = "READY"; // Default READY per compatibilità

        public DocumentInfo(String filename, int chunks, Long timestamp) {
            this.filename = filename;
            this.chunks = chunks;
            this.timestamp = timestamp;
        }

        public String getFilename() {
            return filename;
        }

        public int getChunks() {
            return chunks;
        }
        
        public void setChunks(int chunks) {
            this.chunks = chunks;
        }

        public Long getTimestamp() {
            return timestamp;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
        
        public String getStatusLabel() {
            if ("PROCESSING".equals(status)) {
                return "⏳ In elaborazione...";
            } else if ("READY".equals(status)) {
                return "✅ Pronto";
            } else if ("ERROR".equals(status)) {
                return "❌ Errore";
            }
            return status;
        }

        public String getFormattedDate() {
            if (timestamp == null) {
                return "-";
            }
            LocalDateTime dateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
            return dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        }
    }
}
