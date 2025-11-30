package com.example.ragclient.views;

import com.example.ragclient.dto.HealthResponse;
import com.example.ragclient.service.RagApiService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Route(value = "status", layout = MainLayout.class)
@PageTitle("System Status | RAG Client")
@Slf4j
public class StatusView extends VerticalLayout {

    private final RagApiService ragApiService;
    private final Div documentApiStatus;
    private final Div queryApiStatus;
    private final Div overallStatus;
    private final Paragraph lastCheckTime;

    public StatusView(RagApiService ragApiService) {
        this.ragApiService = ragApiService;

        setSpacing(true);
        setPadding(true);
        setMaxWidth("800px");
        getStyle().set("margin", "0 auto");

        // Header
        H2 title = new H2("💚 System Status");
        
        Button checkButton = new Button("🔄 Check Now", VaadinIcon.REFRESH.create(), 
            event -> checkStatus());
        checkButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        
        HorizontalLayout headerLayout = new HorizontalLayout(title, checkButton);
        headerLayout.setWidthFull();
        headerLayout.setJustifyContentMode(JustifyContentMode.BETWEEN);
        headerLayout.setAlignItems(Alignment.CENTER);
        add(headerLayout);

        // Description
        Paragraph description = new Paragraph(
            "Check the health status of the RAG backend services."
        );
        description.getStyle().set("color", "var(--lumo-secondary-text-color)");
        add(description);

        // Overall status card
        overallStatus = createStatusCard("Overall System", "CHECKING", "⏳");
        add(overallStatus);

        // Individual service status
        H3 servicesTitle = new H3("Services:");
        add(servicesTitle);

        documentApiStatus = createStatusCard("Document Processing API", "CHECKING", "⏳");
        add(documentApiStatus);

        queryApiStatus = createStatusCard("Query API", "CHECKING", "⏳");
        add(queryApiStatus);

        // Last check time
        lastCheckTime = new Paragraph();
        lastCheckTime.getStyle()
            .set("font-size", "var(--lumo-font-size-s)")
            .set("color", "var(--lumo-secondary-text-color)")
            .set("text-align", "center");
        add(lastCheckTime);

        // Connection info
        H3 connectionTitle = new H3("Connection Info:");
        add(connectionTitle);

        Div connectionInfo = new Div();
        connectionInfo.getStyle()
            .set("padding", "var(--lumo-space-m)")
            .set("background", "var(--lumo-contrast-5pct)")
            .set("border-radius", "var(--lumo-border-radius)");
        
        String baseUrl = "http://localhost:8092"; // Could be from config
        Paragraph urlInfo = new Paragraph("🌐 Backend URL: " + baseUrl);
        Paragraph modelInfo = new Paragraph("🤖 LLM Model: Loading...");
        modelInfo.setId("model-info");
        connectionInfo.add(urlInfo, modelInfo);
        add(connectionInfo);

        // Initial check
        checkStatus();
    }

    private Div createStatusCard(String serviceName, String status, String icon) {
        Div card = new Div();
        card.getStyle()
            .set("padding", "var(--lumo-space-m)")
            .set("border", "1px solid var(--lumo-contrast-20pct)")
            .set("border-radius", "var(--lumo-border-radius)")
            .set("background", "var(--lumo-base-color)");

        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setJustifyContentMode(JustifyContentMode.BETWEEN);
        layout.setAlignItems(Alignment.CENTER);

        Span nameSpan = new Span(serviceName);
        nameSpan.getStyle().set("font-weight", "bold");

        Span statusSpan = new Span(icon + " " + status);
        updateStatusColor(statusSpan, status);

        layout.add(nameSpan, statusSpan);
        card.add(layout);

        return card;
    }

    private void updateStatusColor(Span statusSpan, String status) {
        if ("UP".equals(status) || "HEALTHY".equals(status)) {
            statusSpan.getStyle()
                .set("color", "var(--lumo-success-color)")
                .set("font-weight", "bold");
        } else if ("DOWN".equals(status) || "UNHEALTHY".equals(status)) {
            statusSpan.getStyle()
                .set("color", "var(--lumo-error-color)")
                .set("font-weight", "bold");
        } else {
            statusSpan.getStyle()
                .set("color", "var(--lumo-warning-color)")
                .set("font-weight", "bold");
        }
    }

    private void checkStatus() {
        try {
            // Check Document API
            HealthResponse docHealth = ragApiService.getDocumentHealth();
            updateStatusCard(documentApiStatus, "Document Processing API", 
                docHealth.getStatus(), docHealth.getStatus().equals("UP") ? "✅" : "❌");

            // Check Query API
            HealthResponse queryHealth = ragApiService.getQueryHealth();
            updateStatusCard(queryApiStatus, "Query API", 
                queryHealth.getStatus(), queryHealth.getStatus().equals("UP") ? "✅" : "❌");
            
            // Update model info
            if (queryHealth.getModel() != null) {
                getElement().executeJs(
                    "document.getElementById('model-info').textContent = '🤖 LLM Model: " + queryHealth.getModel() + "'"
                );
            }

            // Update overall status
            boolean isHealthy = docHealth.getStatus().equals("UP") && 
                               queryHealth.getStatus().equals("UP");
            updateStatusCard(overallStatus, "Overall System", 
                isHealthy ? "HEALTHY" : "UNHEALTHY", 
                isHealthy ? "✅" : "❌");

            // Update last check time
            String currentTime = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            lastCheckTime.setText("Last checked: " + currentTime);

            // Show notification
            if (isHealthy) {
                Notification notification = Notification.show(
                    "✅ All systems operational",
                    3000,
                    Notification.Position.BOTTOM_END
                );
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } else {
                Notification notification = Notification.show(
                    "⚠️ Some services are down",
                    5000,
                    Notification.Position.MIDDLE
                );
                notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
            }

        } catch (Exception e) {
            log.error("Error checking status", e);
            
            updateStatusCard(documentApiStatus, "Document Processing API", "DOWN", "❌");
            updateStatusCard(queryApiStatus, "Query API", "DOWN", "❌");
            updateStatusCard(overallStatus, "Overall System", "UNHEALTHY", "❌");

            String currentTime = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            lastCheckTime.setText("Last checked: " + currentTime + " (Failed)");

            Notification notification = Notification.show(
                "❌ Cannot connect to backend: " + e.getMessage(),
                5000,
                Notification.Position.MIDDLE
            );
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void updateStatusCard(Div card, String serviceName, String status, String icon) {
        card.removeAll();

        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setJustifyContentMode(JustifyContentMode.BETWEEN);
        layout.setAlignItems(Alignment.CENTER);

        Span nameSpan = new Span(serviceName);
        nameSpan.getStyle().set("font-weight", "bold");

        Span statusSpan = new Span(icon + " " + status);
        updateStatusColor(statusSpan, status);

        layout.add(nameSpan, statusSpan);
        card.add(layout);
    }
}
