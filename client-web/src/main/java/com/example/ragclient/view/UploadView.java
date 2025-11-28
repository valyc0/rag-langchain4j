package com.example.ragclient.view;

import com.example.ragclient.model.UploadResponse;
import com.example.ragclient.service.RagApiService;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.io.IOException;

/**
 * View per l'upload di documenti
 */
@Route(value = "upload", layout = MainLayout.class)
@PageTitle("Upload Documenti")
public class UploadView extends VerticalLayout {

    private final RagApiService ragApiService;
    private final ProgressBar progressBar;
    private final Div resultDiv;
    private Upload upload;

    public UploadView(RagApiService ragApiService) {
        this.ragApiService = ragApiService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.CENTER);

        // Header
        H2 title = new H2("📤 Upload Documenti");
        title.addClassName(LumoUtility.Margin.NONE);

        // Istruzioni
        Paragraph instructions = new Paragraph(
                "Carica documenti per indicizzarli nel sistema RAG. " +
                "Formati supportati: PDF, Word, Excel, PowerPoint, TXT, HTML, XML"
        );
        instructions.getStyle().set("color", "var(--lumo-secondary-text-color)");

        // Upload component
        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(
                ".pdf", ".doc", ".docx", 
                ".xls", ".xlsx", 
                ".ppt", ".pptx",
                ".txt", ".html", ".xml"
        );
        upload.setMaxFileSize(100 * 1024 * 1024); // 100MB
        upload.setDropLabel(new Span("Trascina qui il file o clicca per selezionare"));
        upload.setUploadButton(null); // Rimuove upload automatico
        upload.setAutoUpload(true); // Abilita auto-upload al receiver locale

        // Progress bar
        progressBar = new ProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setWidth("400px");

        // Result div
        resultDiv = new Div();
        resultDiv.getStyle()
                .set("padding", "var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("margin-top", "var(--lumo-space-m)")
                .set("max-width", "500px")
                .set("width", "100%");
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
                            
                            // Interrompe l'upload e pulisce lo stato
                            upload.interruptUpload();
                            upload.clearFileList();
                            
                            showResult(response);
                        }));
                    } catch (Exception e) {
                        getUI().ifPresent(ui -> ui.access(() -> {
                            progressBar.setVisible(false);
                            upload.clearFileList(); // Pulisce anche in caso di errore
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
            upload.clearFileList(); // Pulisce la lista anche in caso di fallimento
            Notification.show("❌ Upload fallito: " + event.getReason().getMessage(), 
                    5000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        });

        // Formati supportati
        Div formatsDiv = createFormatsInfo();

        add(title, instructions, upload, progressBar, resultDiv, formatsDiv);
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

    private Div createFormatsInfo() {
        Div formatsDiv = new Div();
        formatsDiv.getStyle()
                .set("margin-top", "var(--lumo-space-xl)")
                .set("padding", "var(--lumo-space-m)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("max-width", "500px");

        Span header = new Span("📋 Formati supportati:");
        header.getStyle()
                .set("font-weight", "bold")
                .set("display", "block")
                .set("margin-bottom", "var(--lumo-space-s)");
        formatsDiv.add(header);

        String[] formats = {
                "✅ PDF (.pdf)",
                "✅ Microsoft Word (.doc, .docx)",
                "✅ Microsoft Excel (.xls, .xlsx)",
                "✅ Microsoft PowerPoint (.ppt, .pptx)",
                "✅ Testo (.txt)",
                "✅ HTML (.html)",
                "✅ XML (.xml)"
        };

        for (String format : formats) {
            Span formatSpan = new Span(format);
            formatSpan.getStyle()
                    .set("display", "block")
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("padding", "var(--lumo-space-xs) 0");
            formatsDiv.add(formatSpan);
        }

        return formatsDiv;
    }
}
