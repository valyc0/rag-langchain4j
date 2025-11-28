package com.example.ragclient.view;

import com.example.ragclient.model.QueryResponse;
import com.example.ragclient.service.RagApiService;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.accordion.Accordion;
import com.vaadin.flow.component.accordion.AccordionPanel;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * View principale per la chat RAG
 */
@Route(value = "chat", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@PageTitle("Chat RAG")
public class ChatView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(ChatView.class);
    
    private final RagApiService ragApiService;
    private final VerticalLayout messagesLayout;
    private final TextField questionField;
    private final Button sendButton;
    private final ProgressBar progressBar;
    private final Scroller scroller;

    public ChatView(RagApiService ragApiService) {
        this.ragApiService = ragApiService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        // Header
        H2 title = new H2("💬 Chat con i tuoi documenti");
        title.addClassName(LumoUtility.Margin.NONE);

        // Area messaggi scrollabile
        messagesLayout = new VerticalLayout();
        messagesLayout.setWidthFull();
        messagesLayout.setPadding(false);
        messagesLayout.setSpacing(true);
        
        scroller = new Scroller(messagesLayout);
        scroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        scroller.setSizeFull();
        scroller.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("background", "var(--lumo-contrast-5pct)");

        // Progress bar
        progressBar = new ProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);

        // Input area
        questionField = new TextField();
        questionField.setPlaceholder("Scrivi la tua domanda sui documenti...");
        questionField.setWidthFull();
        questionField.setClearButtonVisible(true);
        questionField.addKeyPressListener(Key.ENTER, e -> sendQuestion());

        sendButton = new Button("Invia", VaadinIcon.PAPERPLANE.create());
        sendButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        sendButton.addClickListener(e -> sendQuestion());

        HorizontalLayout inputLayout = new HorizontalLayout(questionField, sendButton);
        inputLayout.setWidthFull();
        inputLayout.setFlexGrow(1, questionField);
        inputLayout.setDefaultVerticalComponentAlignment(Alignment.END);

        // Messaggio di benvenuto
        addWelcomeMessage();

        add(title, scroller, progressBar, inputLayout);
        setFlexGrow(1, scroller);
    }

    private void addWelcomeMessage() {
        Div welcomeMessage = createBotMessage(
                "👋 Ciao! Sono il tuo assistente RAG. " +
                "Puoi farmi domande sui documenti che sono stati caricati nel sistema. " +
                "Scrivimi qualcosa per iniziare!"
        );
        messagesLayout.add(welcomeMessage);
    }

    private void sendQuestion() {
        String question = questionField.getValue().trim();
        if (question.isEmpty()) {
            return;
        }

        log.info("=== INVIO DOMANDA: {}", question);

        // Aggiungi messaggio utente
        messagesLayout.add(createUserMessage(question));
        questionField.clear();

        // Mostra progress
        setLoading(true);
        
        // Aggiungi indicatore di digitazione (puntini pensanti)
        Div typingIndicator = createTypingIndicator();
        messagesLayout.add(typingIndicator);
        
        // Scroll in basso
        scrollToBottom();
        
        log.info("=== Chiamo ragApiService.query()...");
        
        try {
            QueryResponse response = ragApiService.query(question);
            log.info("=== Risposta ricevuta: {}", response != null ? response.getAnswer() : "NULL");
            
            // Rimuovi indicatore di digitazione
            messagesLayout.remove(typingIndicator);
            setLoading(false);
            
            // Aggiungi risposta bot con effetto streaming
            String answer = (response != null && response.getAnswer() != null) 
                    ? response.getAnswer() 
                    : "❌ Nessuna risposta ricevuta dal server";
            
            log.info("=== Aggiungo messaggio bot con streaming: {} chars", answer.length());
            
            // Crea il messaggio bot e mostra con effetto typing
            Div botMessage = createBotMessageStreaming(answer, response);
            messagesLayout.add(botMessage);
            
            // Scroll in basso e focus
            scrollToBottom();
            questionField.focus();
            
        } catch (Exception ex) {
            log.error("=== ERRORE: {}", ex.getMessage(), ex);
            messagesLayout.remove(typingIndicator);
            setLoading(false);
            messagesLayout.add(createBotMessage("❌ Errore: " + ex.getMessage()));
            questionField.focus();
        }
    }
    
    private void scrollToBottom() {
        // JavaScript per scroll automatico
        scroller.getElement().executeJs("this.scrollTop = this.scrollHeight");
    }

    private void setLoading(boolean loading) {
        progressBar.setVisible(loading);
        sendButton.setEnabled(!loading);
        questionField.setEnabled(!loading);
    }

    private Div createUserMessage(String text) {
        Div message = new Div();
        message.addClassName("chat-message-user");
        message.getStyle()
                .set("background", "var(--lumo-primary-color)")
                .set("color", "var(--lumo-primary-contrast-color)")
                .set("padding", "var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("margin-left", "20%")
                .set("margin-right", "0")
                .set("max-width", "80%");

        Span timeSpan = new Span(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        timeSpan.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("opacity", "0.7")
                .set("display", "block")
                .set("text-align", "right");

        Paragraph textP = new Paragraph(text);
        textP.getStyle().set("margin", "0");

        message.add(textP, timeSpan);
        return message;
    }

    private Div createBotMessage(String text) {
        Div message = new Div();
        message.addClassName("chat-message-bot");
        message.getStyle()
                .set("background", "var(--lumo-contrast-10pct)")
                .set("padding", "var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("margin-right", "20%")
                .set("margin-left", "0")
                .set("max-width", "80%");

        Span botLabel = new Span("🤖 RAG Assistant");
        botLabel.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("font-weight", "bold")
                .set("color", "var(--lumo-primary-color)")
                .set("display", "block")
                .set("margin-bottom", "var(--lumo-space-xs)");

        Paragraph textP = new Paragraph(text);
        textP.getStyle().set("margin", "0").set("white-space", "pre-wrap");

        Span timeSpan = new Span(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        timeSpan.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("opacity", "0.7")
                .set("display", "block")
                .set("margin-top", "var(--lumo-space-xs)");

        message.add(botLabel, textP, timeSpan);
        return message;
    }

    /**
     * Crea indicatore di digitazione (puntini pensanti)
     */
    private Div createTypingIndicator() {
        Div message = new Div();
        message.addClassName("chat-message-bot");
        message.getStyle()
                .set("background", "var(--lumo-contrast-10pct)")
                .set("padding", "var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("margin-right", "20%")
                .set("margin-left", "0")
                .set("max-width", "80%");

        Span botLabel = new Span("🤖 RAG Assistant");
        botLabel.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("font-weight", "bold")
                .set("color", "var(--lumo-primary-color)")
                .set("display", "block")
                .set("margin-bottom", "var(--lumo-space-xs)");

        // Contenitore puntini
        Div dotsContainer = new Div();
        dotsContainer.getStyle()
                .set("display", "flex")
                .set("gap", "4px")
                .set("align-items", "center")
                .set("height", "20px");

        // Crea 3 puntini animati
        for (int i = 0; i < 3; i++) {
            Span dot = new Span("●");
            dot.getStyle()
                    .set("color", "var(--lumo-primary-color)")
                    .set("animation", "typing-dot 1.4s infinite")
                    .set("animation-delay", (i * 0.2) + "s");
            dotsContainer.add(dot);
        }

        message.add(botLabel, dotsContainer);

        // Aggiungi CSS per animazione
        message.getElement().executeJs(
            "const style = document.createElement('style');" +
            "style.textContent = `" +
            "@keyframes typing-dot {" +
            "  0%, 60%, 100% { opacity: 0.3; transform: scale(1); }" +
            "  30% { opacity: 1; transform: scale(1.2); }" +
            "}`;" +
            "if (!document.getElementById('typing-animation-style')) {" +
            "  style.id = 'typing-animation-style';" +
            "  document.head.appendChild(style);" +
            "}"
        );

        return message;
    }

    /**
     * Crea messaggio bot con la risposta (senza effetto typing per evitare bug JS)
     */
    private Div createBotMessageStreaming(String text, QueryResponse response) {
        Div message = new Div();
        message.addClassName("chat-message-bot");
        message.getStyle()
                .set("background", "var(--lumo-contrast-10pct)")
                .set("padding", "var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("margin-right", "20%")
                .set("margin-left", "0")
                .set("max-width", "80%");

        Span botLabel = new Span("🤖 RAG Assistant");
        botLabel.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("font-weight", "bold")
                .set("color", "var(--lumo-primary-color)")
                .set("display", "block")
                .set("margin-bottom", "var(--lumo-space-xs)");

        // Testo della risposta (diretto, senza animazione)
        Paragraph textP = new Paragraph(text);
        textP.getStyle()
                .set("margin", "0")
                .set("white-space", "pre-wrap")
                .set("word-wrap", "break-word");

        Span timeSpan = new Span(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        timeSpan.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("opacity", "0.7")
                .set("display", "block")
                .set("margin-top", "var(--lumo-space-xs)");

        message.add(botLabel, textP, timeSpan);
        
        // Aggiungi fonti (se presenti)
        if (response != null && response.getSources() != null && !response.getSources().isEmpty()) {
            Div sourcesDiv = createSourcesMessage(response);
            message.add(sourcesDiv);
        }
        
        return message;
    }

    private Div createSourcesMessage(QueryResponse response) {
        Div sourcesContainer = new Div();
        sourcesContainer.getStyle()
                .set("margin-top", "var(--lumo-space-s)");

        // Contenitore delle fonti
        VerticalLayout sourcesContent = new VerticalLayout();
        sourcesContent.setPadding(false);
        sourcesContent.setSpacing(true);
        sourcesContent.getStyle()
                .set("padding-top", "var(--lumo-space-s)");

        for (QueryResponse.Source source : response.getSources()) {
            Div sourceItem = new Div();
            sourceItem.getStyle()
                    .set("padding", "var(--lumo-space-xs)")
                    .set("background", "var(--lumo-contrast-5pct)")
                    .set("border-radius", "var(--lumo-border-radius-s)");

            // Header con nome file e score
            String scorePercent = String.format("%.0f%%", source.getScore() * 100);
            Span fileSpan = new Span("📄 " + source.getFilename() + " (" + scorePercent + ")");
            fileSpan.getStyle()
                    .set("font-weight", "bold")
                    .set("display", "block")
                    .set("margin-bottom", "var(--lumo-space-xs)");

            String truncatedText = source.getText().length() > 200 
                    ? source.getText().substring(0, 200) + "..." 
                    : source.getText();
            Span textSpan = new Span(truncatedText);
            textSpan.getStyle()
                    .set("font-size", "var(--lumo-font-size-xs)")
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("display", "block")
                    .set("font-style", "italic");

            sourceItem.add(fileSpan, textSpan);
            sourcesContent.add(sourceItem);
        }

        // Accordion per fonti (inizialmente chiuso)
        Accordion accordion = new Accordion();
        accordion.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("border-left", "3px solid var(--lumo-primary-color)");
        
        AccordionPanel panel = accordion.add("📚 Fonti utilizzate (" + response.getChunksUsed() + " chunks)", sourcesContent);
        
        // Chiudi esplicitamente l'accordion e il pannello
        accordion.close();
        panel.setOpened(false);
        
        sourcesContainer.add(accordion);
        return sourcesContainer;
    }
}
