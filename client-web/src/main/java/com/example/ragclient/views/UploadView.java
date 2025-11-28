package com.example.ragclient.views;

import com.example.ragclient.dto.UploadResponse;
import com.example.ragclient.service.RagApiService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;

@Route(value = "upload", layout = MainLayout.class)
@PageTitle("Upload Document | RAG Client")
@Slf4j
public class UploadView extends VerticalLayout {

    private final RagApiService ragApiService;
    private final MemoryBuffer buffer;
    private final Upload upload;
    private final Paragraph statusLabel;

    public UploadView(RagApiService ragApiService) {
        this.ragApiService = ragApiService;
        this.buffer = new MemoryBuffer();

        setSpacing(true);
        setPadding(true);
        setMaxWidth("800px");
        getStyle().set("margin", "0 auto");

        // Header
        H2 title = new H2("📤 Upload Document");
        add(title);

        // Description
        Paragraph description = new Paragraph(
            "Upload PDF, Word, Excel, PowerPoint, TXT, or HTML files to index them in the RAG system."
        );
        description.getStyle().set("color", "var(--lumo-secondary-text-color)");
        add(description);

        // Upload component
        upload = new Upload(buffer);
        upload.setAcceptedFileTypes(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/html",
            "application/xml"
        );
        upload.setMaxFileSize(100 * 1024 * 1024); // 100MB
        upload.setMaxFiles(1);
        upload.setDropLabel(new Paragraph("Drop file here or click to browse"));
        
        upload.addSucceededListener(event -> {
            String filename = event.getFileName();
            uploadFile(filename);
        });

        upload.addFailedListener(event -> {
            Notification notification = Notification.show(
                "❌ Upload failed: " + event.getReason().getMessage(),
                5000,
                Notification.Position.MIDDLE
            );
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        });

        add(upload);

        // Status label
        statusLabel = new Paragraph();
        statusLabel.setVisible(false);
        add(statusLabel);
    }

    private void uploadFile(String filename) {
        try {
            statusLabel.setText("⏳ Uploading and processing " + filename + "...");
            statusLabel.setVisible(true);

            // Read file content
            InputStream inputStream = buffer.getInputStream();
            byte[] content = inputStream.readAllBytes();

            // Upload to backend
            UploadResponse response = ragApiService.uploadDocument(filename, content);

            // Show success notification
            Notification notification = Notification.show(
                response.getMessage(),
                5000,
                Notification.Position.MIDDLE
            );
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            // Update status
            Object status = response.getData().get("status");
            Object sizeBytes = response.getData().get("size_bytes");
            Object chunks = response.getData().get("chunks_created");
            
            String statusText = "✅ File uploaded successfully!\n" +
                "Status: " + status + "\n" +
                "Size: " + formatBytes((Integer) sizeBytes);
            
            if (chunks != null) {
                statusText += "\nChunks created: " + chunks;
            }
            
            statusLabel.setText(statusText);
            statusLabel.getStyle().set("white-space", "pre-line");
            statusLabel.getStyle().set("color", "var(--lumo-success-color)");

            // Clear upload component
            upload.clearFileList();

        } catch (Exception e) {
            log.error("Error uploading file", e);
            Notification notification = Notification.show(
                "❌ Error: " + e.getMessage(),
                5000,
                Notification.Position.MIDDLE
            );
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            
            statusLabel.setText("❌ Upload failed: " + e.getMessage());
            statusLabel.getStyle().set("color", "var(--lumo-error-color)");
        }
    }

    private String formatBytes(Integer bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
}
