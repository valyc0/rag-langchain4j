package com.example.ragclient.view;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.theme.lumo.LumoUtility;

/**
 * Layout principale dell'applicazione con menu laterale
 */
public class MainLayout extends AppLayout {

    public MainLayout() {
        createHeader();
        createDrawer();
    }

    private void createHeader() {
        H1 logo = new H1("🤖 RAG Client");
        logo.addClassNames(
                LumoUtility.FontSize.LARGE,
                LumoUtility.Margin.MEDIUM
        );

        HorizontalLayout header = new HorizontalLayout(
                new DrawerToggle(),
                logo
        );
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setWidthFull();
        header.addClassNames(
                LumoUtility.Padding.Vertical.NONE,
                LumoUtility.Padding.Horizontal.MEDIUM
        );

        addToNavbar(header);
    }

    private void createDrawer() {
        SideNav nav = new SideNav();
        
        nav.addItem(new SideNavItem("💬 Chat RAG", ChatView.class, VaadinIcon.CHAT.create()));
        nav.addItem(new SideNavItem("📄 Documenti", DocumentsView.class, VaadinIcon.FILE_TEXT.create()));
        nav.addItem(new SideNavItem("📤 Upload", UploadView.class, VaadinIcon.UPLOAD.create()));
        nav.addItem(new SideNavItem("🔧 Status", StatusView.class, VaadinIcon.DASHBOARD.create()));

        addToDrawer(nav);
    }
}
