package com.example.ragclient.view;

import com.example.ragclient.model.DocumentListResponse;
import com.example.ragclient.model.DeleteResponse;
import com.example.ragclient.model.UploadResponse;
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

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * View per upload e visualizzazione documenti indicizzati
 */
@Route(value = "documents", layout = MainLayout.class)
@PageTitle("Documenti")
public class DocumentsView extends VerticalLayout {

    private final RagApiService ragApiService;
    private final Grid<DocumentInfo> grid;
    private final Span totalDocsSpan;
    private final Span totalChunksSpan;
    private ProgressBar progressBar;
    private Div resultDiv;
    private Upload upload;

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
        grid.addColumn(DocumentInfo::getFormattedDate).setHeader("📅 Data").setAutoWidth(true);
        
        // Colonna azioni
        grid.addComponentColumn(documentInfo -> {
            Button deleteButton = new Button("Elimina", VaadinIcon.TRASH.create());
            deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            deleteButton.addClickListener(e -> confirmDelete(documentInfo));
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
                
                // Upload al backend in un thread separato
                new Thread(() -> {
                    try {
                        UploadResponse response = ragApiService.uploadDocument(filename, content);
                        
                        getUI().ifPresent(ui -> ui.access(() -> {
                            progressBar.setVisible(false);
                            upload.interruptUpload();
                            upload.clearFileList();
                            
                            showResult(response);
                            
                            // Refresh della grid dopo upload
                            if (response.getMessage() != null && response.getMessage().contains("✅")) {
                                loadDocuments();
                            }
                        }));
                    } catch (Exception e) {
                        getUI().ifPresent(ui -> ui.access(() -> {
                            progressBar.setVisible(false);
                            upload.clearFileList();
                            Notification.show("❌ Errore durante l'upload: " + e.getMessage(), 
                                    5000, Notification.Position.BOTTOM_CENTER)
                                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
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
            
            Paragraph errorMsg = new Paragraph(response.getMessage());
            errorMsg.getStyle().set("margin", "var(--lumo-space-s) 0 0 0");
            
            resultDiv.add(errorIcon, errorMsg);
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
            DocumentListResponse response = ragApiService.getDocumentsList();
            
            if (response != null) {
                totalDocsSpan.setText("📚 Documenti: " + response.getTotalDocuments());
                totalChunksSpan.setText("📊 Chunks totali: " + response.getTotalChunks());

                List<DocumentInfo> documents = new ArrayList<>();
                if (response.getDocuments() != null) {
                    for (Map.Entry<String, Integer> entry : response.getDocuments().entrySet()) {
                        String filename = entry.getKey();
                        int chunks = entry.getValue();
                        Long timestamp = response.getTimestamps() != null 
                                ? response.getTimestamps().get(filename) 
                                : null;
                        documents.add(new DocumentInfo(filename, chunks, timestamp));
                    }
                }
                grid.setItems(documents);
            }
        } catch (Exception e) {
            Notification.show("❌ Errore nel caricamento documenti: " + e.getMessage(), 
                    5000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
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
        private final int chunks;
        private final Long timestamp;

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

        public Long getTimestamp() {
            return timestamp;
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
