package com.example.ragclient.views;

import com.example.ragclient.dto.DocumentListResponse;
import com.example.ragclient.service.RagApiService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
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
public class DocumentListView extends VerticalLayout {

    private final RagApiService ragApiService;
    private final Grid<DocumentItem> grid;
    private final Paragraph statsLabel;

    @Data
    @AllArgsConstructor
    public static class DocumentItem {
        private String filename;
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
        
        grid.addColumn(DocumentItem::getChunks)
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
            deleteButton.addClickListener(event -> confirmDelete(item.getFilename()));
            return deleteButton;
        }).setHeader("Actions").setFlexGrow(1);

        add(grid);

        // Load initial data
        loadDocuments();
    }

    private void loadDocuments() {
        try {
            DocumentListResponse response = ragApiService.getDocumentList();
            
            // Update stats
            statsLabel.setText(String.format(
                "📊 Total: %d documents, %d chunks",
                response.getTotal_documents(),
                response.getTotal_chunks()
            ));

            // Prepare grid data
            List<DocumentItem> items = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            
            if (response.getDocuments() != null) {
                for (Map.Entry<String, Integer> entry : response.getDocuments().entrySet()) {
                    String filename = entry.getKey();
                    Integer chunks = entry.getValue();
                    Long timestamp = response.getTimestamps() != null ? 
                        response.getTimestamps().get(filename) : null;
                    String formattedDate = timestamp != null ? 
                        sdf.format(new Date(timestamp)) : "N/A";
                    
                    items.add(new DocumentItem(filename, chunks, timestamp, formattedDate));
                }
            }

            grid.setItems(items);

            if (items.isEmpty()) {
                Notification.show("ℹ️ No documents found. Upload some documents first!", 
                    3000, Notification.Position.MIDDLE);
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
