package com.example.ragclient.views;

import com.example.ragclient.service.RagApiService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Route(value = "documents", layout = MainLayout.class)
@PageTitle("Documents | RAG Client")
@Slf4j
public class DocumentListView extends VerticalLayout implements BeforeEnterObserver {

    private final RagApiService ragApiService;
    private final Grid<DocumentItem> grid;
    private final Paragraph statsLabel;
    private com.vaadin.flow.shared.Registration pollingRegistration;

    @Data
    @AllArgsConstructor
    public static class DocumentItem {
        private String filename;
        private String status;
        private Integer chunks;
        private Long timestamp;
        private String formattedDate;
    }

    public DocumentListView(RagApiService ragApiService) {
        this.ragApiService = ragApiService;

        setSpacing(true);
        setPadding(true);
        setMaxWidth("1200px");
        getStyle().set("margin", "0 auto");

        // Header
        H2 title = new H2("📚 Indexed Documents");
        
        // Refresh button
        Button refreshButton = new Button("🔄 Refresh", VaadinIcon.REFRESH.create(), 
            event -> loadDocuments());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        
        HorizontalLayout headerLayout = new HorizontalLayout(title, refreshButton);
        headerLayout.setWidthFull();
        headerLayout.setJustifyContentMode(JustifyContentMode.BETWEEN);
        headerLayout.setAlignItems(Alignment.CENTER);
        add(headerLayout);

        // Stats label
        statsLabel = new Paragraph();
        statsLabel.getStyle().set("color", "var(--lumo-secondary-text-color)");
        add(statsLabel);

        // Grid
        grid = new Grid<>(DocumentItem.class, false);
        grid.setHeight("600px");

        grid.addColumn(DocumentItem::getFilename)
            .setHeader("📄 Filename")
            .setFlexGrow(3)
            .setSortable(true);

        grid.addComponentColumn(item -> statusBadge(item.getStatus()))
            .setHeader("🔄 Status")
            .setFlexGrow(1);

        grid.addColumn(item -> item.getStatus().equals("READY") ? String.valueOf(item.getChunks()) : "—")
            .setHeader("📊 Chunks")
            .setFlexGrow(1)
            .setSortable(true);

        grid.addColumn(DocumentItem::getFormattedDate)
            .setHeader("📅 Upload Date")
            .setFlexGrow(2)
            .setSortable(true);

        grid.addComponentColumn(item -> {
            Button deleteButton = new Button("Delete", VaadinIcon.TRASH.create());
            deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            // Disabilita delete se in PROCESSING
            deleteButton.setEnabled(!"PROCESSING".equals(item.getStatus()));
            deleteButton.addClickListener(event -> confirmDelete(item.getFilename()));
            return deleteButton;
        }).setHeader("Actions").setFlexGrow(1);

        add(grid);

        // Load initial data
        loadDocuments();
    }

    private void loadDocuments() {
        try {
            // Usa /statuses: restituisce TUTTI i documenti in tutti gli stati
            @SuppressWarnings("unchecked")
            Map<String, Object> statuses = ragApiService.getAllDocumentStatuses();

            List<DocumentItem> items = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            boolean hasProcessing = false;

            if (statuses != null) {
                for (Map.Entry<String, Object> entry : statuses.entrySet()) {
                    String filename = entry.getKey();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> info = (Map<String, Object>) entry.getValue();

                    String status = (String) info.getOrDefault("status", "UNKNOWN");
                    Number chunksNum = (Number) info.getOrDefault("chunks", 0);
                    Integer chunks = chunksNum != null ? chunksNum.intValue() : 0;
                    Number tsNum = (Number) info.getOrDefault("uploadTimestamp", null);
                    Long timestamp = tsNum != null ? tsNum.longValue() : null;
                    String formattedDate = timestamp != null ? sdf.format(new Date(timestamp)) : "N/A";

                    items.add(new DocumentItem(filename, status, chunks, timestamp, formattedDate));
                    if ("PROCESSING".equals(status)) hasProcessing = true;
                }
            }

            // Ordina: PROCESSING prima, poi READY, poi ERROR
            items.sort((a, b) -> statusOrder(a.getStatus()) - statusOrder(b.getStatus()));

            grid.setItems(items);

            long ready = items.stream().filter(i -> "READY".equals(i.getStatus())).count();
            long processing = items.stream().filter(i -> "PROCESSING".equals(i.getStatus())).count();
            long error = items.stream().filter(i -> "ERROR".equals(i.getStatus())).count();
            int totalChunks = items.stream().filter(i -> "READY".equals(i.getStatus()))
                .mapToInt(DocumentItem::getChunks).sum();

            statsLabel.setText(String.format(
                "📊 Totale: %d documenti  |  ✅ READY: %d (%d chunks)  |  ⏳ PROCESSING: %d  |  ❌ ERROR: %d",
                items.size(), ready, totalChunks, processing, error
            ));

            // Auto-refresh ogni 3s se ci sono documenti in elaborazione
            if (hasProcessing) {
                startPolling();
            } else {
                stopPolling();
            }

        } catch (Exception e) {
            log.error("Error loading documents", e);
            Notification notification = Notification.show(
                "❌ Error loading documents: " + e.getMessage(),
                5000,
                Notification.Position.MIDDLE
            );
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private int statusOrder(String status) {
        return switch (status) {
            case "PROCESSING" -> 0;
            case "READY"      -> 1;
            case "ERROR"      -> 2;
            default           -> 3;
        };
    }

    private Span statusBadge(String status) {
        Span badge = new Span(status);
        badge.getStyle()
            .set("padding", "2px 10px")
            .set("border-radius", "12px")
            .set("font-size", "0.8em")
            .set("font-weight", "bold");
        switch (status) {
            case "READY"      -> badge.getStyle()
                .set("background", "#e8f5e9").set("color", "#2e7d32");
            case "PROCESSING" -> badge.getStyle()
                .set("background", "#fff8e1").set("color", "#f57f17");
            case "ERROR"      -> badge.getStyle()
                .set("background", "#ffebee").set("color", "#c62828");
            default           -> badge.getStyle()
                .set("background", "#f5f5f5").set("color", "#616161");
        }
        return badge;
    }

    private void startPolling() {
        if (pollingRegistration != null) return; // già attivo
        pollingRegistration = UI.getCurrent().addPollListener(event -> loadDocuments());
        UI.getCurrent().setPollInterval(3000);
        log.debug("🔄 Auto-refresh avviato (3s)");
    }

    private void stopPolling() {
        if (pollingRegistration != null) {
            pollingRegistration.remove();
            pollingRegistration = null;
            UI.getCurrent().setPollInterval(-1);
            log.debug("⏹️ Auto-refresh fermato");
        }
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Ricarica sempre quando si entra nella vista
        loadDocuments();
    }

    @Override
    protected void onDetach(com.vaadin.flow.component.DetachEvent detachEvent) {
        stopPolling();
        super.onDetach(detachEvent);
    }

    private void confirmDelete(String filename) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete Document");
        dialog.setText("Are you sure you want to delete \"" + filename + "\"? This action cannot be undone.");
        
        dialog.setCancelable(true);
        dialog.setCancelText("Cancel");
        
        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");
        
        dialog.addConfirmListener(event -> deleteDocument(filename));
        
        dialog.open();
    }

    private void deleteDocument(String filename) {
        try {
            Map<String, Object> response = ragApiService.deleteDocument(filename);
            
            String status = (String) response.get("status");
            String message = (String) response.get("message");
            
            if ("success".equals(status)) {
                Notification notification = Notification.show(
                    message,
                    3000,
                    Notification.Position.MIDDLE
                );
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                
                // Reload documents
                loadDocuments();
            } else {
                Notification notification = Notification.show(
                    "❌ " + message,
                    5000,
                    Notification.Position.MIDDLE
                );
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }

        } catch (Exception e) {
            log.error("Error deleting document", e);
            Notification notification = Notification.show(
                "❌ Error deleting document: " + e.getMessage(),
                5000,
                Notification.Position.MIDDLE
            );
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
