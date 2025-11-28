package com.example.ragclient.view;

import com.example.ragclient.model.DocumentListResponse;
import com.example.ragclient.model.DeleteResponse;
import com.example.ragclient.service.RagApiService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * View per visualizzare i documenti indicizzati
 */
@Route(value = "documents", layout = MainLayout.class)
@PageTitle("Documenti")
public class DocumentsView extends VerticalLayout {

    private final RagApiService ragApiService;
    private final Grid<DocumentInfo> grid;
    private final Span totalDocsSpan;
    private final Span totalChunksSpan;

    public DocumentsView(RagApiService ragApiService) {
        this.ragApiService = ragApiService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        // Header
        H2 title = new H2("📄 Documenti Indicizzati");
        title.addClassName(LumoUtility.Margin.NONE);

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

        add(title, statsLayout, grid);
        setFlexGrow(1, grid);

        // Carica dati
        loadDocuments();
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
