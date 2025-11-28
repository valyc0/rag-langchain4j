package com.example.ragclient.views;

import com.example.ragclient.dto.QueryResponse;
import com.example.ragclient.service.RagApiService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Query RAG | RAG Client")
@Slf4j
public class QueryView extends VerticalLayout {

    private final RagApiService ragApiService;
    private final TextArea questionField;
    private final Button queryButton;
    private final VerticalLayout resultsLayout;

    public QueryView(RagApiService ragApiService) {
        this.ragApiService = ragApiService;

        setSpacing(true);
        setPadding(true);
        setMaxWidth("1200px");
        getStyle().set("margin", "0 auto");

        // Header
        H2 title = new H2("❓ Ask Questions");
        add(title);

        // Description
        Paragraph description = new Paragraph(
            "Ask questions about your indexed documents. The system will find relevant information and generate an answer."
        );
        description.getStyle().set("color", "var(--lumo-secondary-text-color)");
        add(description);

        // Question input
        questionField = new TextArea("Your Question");
        questionField.setWidthFull();
        questionField.setPlaceholder("E.g., What is this document about? Summarize the main points...");
        questionField.setHeight("120px");
        add(questionField);

        // Query button
        queryButton = new Button("🔍 Ask Question", event -> executeQuery());
        queryButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        queryButton.addClickShortcut(com.vaadin.flow.component.Key.ENTER, 
                                     com.vaadin.flow.component.KeyModifier.CONTROL);
        
        HorizontalLayout buttonLayout = new HorizontalLayout(queryButton);
        add(buttonLayout);

        // Keyboard shortcut hint
        Paragraph hint = new Paragraph("💡 Tip: Press Ctrl+Enter to submit");
        hint.getStyle()
            .set("font-size", "var(--lumo-font-size-s)")
            .set("color", "var(--lumo-secondary-text-color)");
        add(hint);

        // Results area
        resultsLayout = new VerticalLayout();
        resultsLayout.setSpacing(true);
        resultsLayout.setPadding(false);
        add(resultsLayout);

        // Focus on question field
        questionField.focus();
    }

    private void executeQuery() {
        String question = questionField.getValue();
        
        if (question == null || question.trim().isEmpty()) {
            Notification.show("⚠️ Please enter a question", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        queryButton.setEnabled(false);
        queryButton.setText("⏳ Processing...");
        resultsLayout.removeAll();

        try {
            // Execute query
            QueryResponse response = ragApiService.query(question.trim());

            // Display results
            displayResults(response);

            queryButton.setEnabled(true);
            queryButton.setText("🔍 Ask Question");

        } catch (Exception e) {
            log.error("Error executing query", e);
            Notification notification = Notification.show(
                "❌ Error: " + e.getMessage(),
                5000,
                Notification.Position.MIDDLE
            );
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);

            queryButton.setEnabled(true);
            queryButton.setText("🔍 Ask Question");
        }
    }

    private void displayResults(QueryResponse response) {
        // Question
        H3 questionTitle = new H3("Question:");
        questionTitle.getStyle().set("margin-top", "var(--lumo-space-l)");
        resultsLayout.add(questionTitle);
        
        Paragraph questionText = new Paragraph(response.getQuestion());
        questionText.getStyle()
            .set("font-style", "italic")
            .set("color", "var(--lumo-secondary-text-color)");
        resultsLayout.add(questionText);

        // Answer
        H3 answerTitle = new H3("Answer:");
        resultsLayout.add(answerTitle);
        
        Div answerDiv = new Div();
        answerDiv.getStyle()
            .set("padding", "var(--lumo-space-m)")
            .set("background", "var(--lumo-contrast-5pct)")
            .set("border-radius", "var(--lumo-border-radius)")
            .set("white-space", "pre-wrap")
            .set("line-height", "1.6");
        answerDiv.setText(response.getAnswer());
        resultsLayout.add(answerDiv);

        // Stats
        if (response.getChunks_used() != null) {
            Paragraph stats = new Paragraph("📊 Used " + response.getChunks_used() + " document chunks");
            stats.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
            resultsLayout.add(stats);
        }

        // Sources
        if (response.getSources() != null && !response.getSources().isEmpty()) {
            H3 sourcesTitle = new H3("📚 Sources:");
            sourcesTitle.getStyle().set("margin-top", "var(--lumo-space-l)");
            resultsLayout.add(sourcesTitle);

            int sourceNum = 1;
            for (QueryResponse.Source source : response.getSources()) {
                VerticalLayout sourceCard = createSourceCard(sourceNum++, source);
                resultsLayout.add(sourceCard);
            }
        }
    }

    private VerticalLayout createSourceCard(int num, QueryResponse.Source source) {
        VerticalLayout card = new VerticalLayout();
        card.setSpacing(false);
        card.setPadding(true);
        card.getStyle()
            .set("border", "1px solid var(--lumo-contrast-20pct)")
            .set("border-radius", "var(--lumo-border-radius)")
            .set("background", "var(--lumo-base-color)");

        // Header
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        
        Span sourceNum = new Span("Source #" + num);
        sourceNum.getStyle().set("font-weight", "bold");
        
        Span filename = new Span("📄 " + source.getFilename());
        filename.getStyle().set("color", "var(--lumo-secondary-text-color)");
        
        Span score = new Span(String.format("⭐ %.2f", source.getScore()));
        score.getStyle()
            .set("color", source.getScore() > 0.8 ? "var(--lumo-success-color)" : "var(--lumo-warning-color)")
            .set("font-weight", "bold");
        
        header.add(sourceNum, filename, score);
        card.add(header);

        // Text content
        Paragraph text = new Paragraph(source.getText());
        text.getStyle()
            .set("margin-top", "var(--lumo-space-s)")
            .set("font-size", "var(--lumo-font-size-s)")
            .set("color", "var(--lumo-secondary-text-color)")
            .set("white-space", "pre-wrap");
        card.add(text);

        return card;
    }
}
