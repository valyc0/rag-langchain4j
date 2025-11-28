package com.example.ragclient.view;

import com.example.ragclient.model.DocumentListResponse;
import com.example.ragclient.model.HealthResponse;
import com.example.ragclient.service.RagApiService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

/**
 * View per lo status del sistema
 */
@Route(value = "status", layout = MainLayout.class)
@PageTitle("Status Sistema")
public class StatusView extends VerticalLayout {

    private final RagApiService ragApiService;
    private final Div documentsHealthCard;
    private final Div queryHealthCard;
    private final Div statsCard;

    public StatusView(RagApiService ragApiService) {
        this.ragApiService = ragApiService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        // Header
        H2 title = new H2("🔧 Status Sistema RAG");
        title.addClassName(LumoUtility.Margin.NONE);

        Button refreshButton = new Button("Aggiorna", VaadinIcon.REFRESH.create());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        refreshButton.addClickListener(e -> refreshStatus());

        HorizontalLayout headerLayout = new HorizontalLayout(title, refreshButton);
        headerLayout.setAlignItems(Alignment.CENTER);
        headerLayout.setWidthFull();
        headerLayout.setJustifyContentMode(JustifyContentMode.BETWEEN);

        // Cards
        documentsHealthCard = createStatusCard("📄 API Documenti", "Caricamento...");
        queryHealthCard = createStatusCard("💬 API Query", "Caricamento...");
        statsCard = createStatusCard("📊 Statistiche", "Caricamento...");

        HorizontalLayout cardsLayout = new HorizontalLayout(documentsHealthCard, queryHealthCard, statsCard);
        cardsLayout.setWidthFull();
        cardsLayout.setSpacing(true);

        // Info backend
        Div backendInfo = createBackendInfo();

        add(headerLayout, cardsLayout, backendInfo);

        // Carica status
        refreshStatus();
    }

    private void refreshStatus() {
        // Documents Health
        new Thread(() -> {
            HealthResponse health = ragApiService.checkDocumentsHealth();
            getUI().ifPresent(ui -> ui.access(() -> 
                    updateHealthCard(documentsHealthCard, health)));
        }).start();

        // Query Health
        new Thread(() -> {
            HealthResponse health = ragApiService.checkQueryHealth();
            getUI().ifPresent(ui -> ui.access(() -> 
                    updateHealthCard(queryHealthCard, health)));
        }).start();

        // Stats
        new Thread(() -> {
            DocumentListResponse docs = ragApiService.getDocumentsList();
            getUI().ifPresent(ui -> ui.access(() -> 
                    updateStatsCard(statsCard, docs)));
        }).start();
    }

    private Div createStatusCard(String title, String content) {
        Div card = new Div();
        card.getStyle()
                .set("padding", "var(--lumo-space-l)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("min-width", "200px")
                .set("flex", "1");

        Span titleSpan = new Span(title);
        titleSpan.getStyle()
                .set("font-weight", "bold")
                .set("font-size", "var(--lumo-font-size-l)")
                .set("display", "block")
                .set("margin-bottom", "var(--lumo-space-m)");

        Span contentSpan = new Span(content);
        contentSpan.getElement().setAttribute("data-content", "true");

        card.add(titleSpan, contentSpan);
        return card;
    }

    private void updateHealthCard(Div card, HealthResponse health) {
        card.getChildren()
                .filter(c -> c.getElement().hasAttribute("data-content"))
                .findFirst()
                .ifPresent(c -> card.remove(c));

        Div statusDiv = new Div();
        statusDiv.getElement().setAttribute("data-content", "true");

        boolean isUp = "UP".equals(health.getStatus());
        
        Span statusSpan = new Span(isUp ? "✅ ONLINE" : "❌ OFFLINE");
        statusSpan.getStyle()
                .set("font-weight", "bold")
                .set("font-size", "var(--lumo-font-size-xl)")
                .set("color", isUp ? "var(--lumo-success-text-color)" : "var(--lumo-error-text-color)")
                .set("display", "block");

        Span serviceSpan = new Span(health.getService() != null ? health.getService() : "-");
        serviceSpan.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("display", "block")
                .set("margin-top", "var(--lumo-space-xs)");

        statusDiv.add(statusSpan, serviceSpan);
        card.add(statusDiv);

        // Update card border color
        card.getStyle().set("border-color", 
                isUp ? "var(--lumo-success-color)" : "var(--lumo-error-color)");
    }

    private void updateStatsCard(Div card, DocumentListResponse docs) {
        card.getChildren()
                .filter(c -> c.getElement().hasAttribute("data-content"))
                .findFirst()
                .ifPresent(c -> card.remove(c));

        Div statsDiv = new Div();
        statsDiv.getElement().setAttribute("data-content", "true");

        if (docs != null) {
            addStatLine(statsDiv, "📚 Documenti", String.valueOf(docs.getTotalDocuments()));
            addStatLine(statsDiv, "📊 Chunks totali", String.valueOf(docs.getTotalChunks()));
            
            if (docs.getDocuments() != null && !docs.getDocuments().isEmpty()) {
                Span filesHeader = new Span("📄 Files:");
                filesHeader.getStyle()
                        .set("font-weight", "bold")
                        .set("display", "block")
                        .set("margin-top", "var(--lumo-space-m)");
                statsDiv.add(filesHeader);

                docs.getDocuments().forEach((filename, chunks) -> {
                    Span fileSpan = new Span("  • " + filename + " (" + chunks + " chunks)");
                    fileSpan.getStyle()
                            .set("display", "block")
                            .set("font-size", "var(--lumo-font-size-s)")
                            .set("color", "var(--lumo-secondary-text-color)");
                    statsDiv.add(fileSpan);
                });
            }
        } else {
            Span errorSpan = new Span("❌ Impossibile caricare statistiche");
            errorSpan.getStyle().set("color", "var(--lumo-error-text-color)");
            statsDiv.add(errorSpan);
        }

        card.add(statsDiv);
    }

    private void addStatLine(Div parent, String label, String value) {
        Div line = new Div();
        line.getStyle().set("margin", "var(--lumo-space-xs) 0");
        
        Span labelSpan = new Span(label + ": ");
        labelSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");
        
        Span valueSpan = new Span(value);
        valueSpan.getStyle().set("font-weight", "bold").set("font-size", "var(--lumo-font-size-l)");
        
        line.add(labelSpan, valueSpan);
        parent.add(line);
    }

    private Div createBackendInfo() {
        Div info = new Div();
        info.getStyle()
                .set("margin-top", "var(--lumo-space-xl)")
                .set("padding", "var(--lumo-space-m)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-m)");

        Span header = new Span("ℹ️ Informazioni Backend");
        header.getStyle()
                .set("font-weight", "bold")
                .set("display", "block")
                .set("margin-bottom", "var(--lumo-space-s)");
        info.add(header);

        String[] lines = {
                "🔗 URL Backend: http://localhost:8092",
                "🗄️ Vector Database: Qdrant (porta 6334)",
                "🤖 LLM: Google Gemini 2.0 Flash",
                "📄 Parser: Apache Tika",
                "⚡ Framework: LangChain4j + Spring Boot 3"
        };

        for (String line : lines) {
            Span lineSpan = new Span(line);
            lineSpan.getStyle()
                    .set("display", "block")
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("padding", "var(--lumo-space-xs) 0");
            info.add(lineSpan);
        }

        return info;
    }
}
