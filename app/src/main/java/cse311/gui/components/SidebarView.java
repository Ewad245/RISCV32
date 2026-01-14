package cse311.gui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import java.util.function.Consumer;

public class SidebarView extends VBox {

    private final Consumer<String> onNavigation;
    private Button btnDashboard;
    private Button btnDatapath;

    public SidebarView(Consumer<String> onNavigation) {
        this.onNavigation = onNavigation;
        initializeUI();
    }

    private void initializeUI() {
        this.setPadding(new Insets(20));
        this.setSpacing(15);
        this.setPrefWidth(200);
        this.setStyle("-fx-background-color: #2c3e50;");
        this.setAlignment(Pos.TOP_CENTER);

        Label lblTitle = new Label("RISC-V OS");
        lblTitle.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold; -fx-padding: 0 0 20 0;");

        btnDashboard = createNavButton("Dashboard", "dashboard");
        btnDatapath = createNavButton("CPU Datapath", "datapath");

        this.getChildren().addAll(lblTitle, btnDashboard, btnDatapath);

        // Set initial active state
        setActiveButton(btnDashboard);
    }

    private Button createNavButton(String text, String viewKey) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefHeight(40);
        btn.setStyle(getButtonStyle(false));

        btn.setOnAction(e -> {
            setActiveButton(btn);
            onNavigation.accept(viewKey);
        });

        // Hover effect
        btn.setOnMouseEntered(e -> {
            if (btn.getStyle().contains("#34495e")) { // Only if inactive
                btn.setStyle(getButtonStyle(false) + "-fx-background-color: #3e5871;");
            }
        });
        btn.setOnMouseExited(e -> {
            if (btn.getStyle().contains("#3e5871")) { // Restore inactive style
                btn.setStyle(getButtonStyle(false));
            }
        });

        return btn;
    }

    private void setActiveButton(Button activeBtn) {
        // Reset all styles
        btnDashboard.setStyle(getButtonStyle(false));
        btnDatapath.setStyle(getButtonStyle(false));

        // Set active style
        activeBtn.setStyle(getButtonStyle(true));
    }

    private String getButtonStyle(boolean isActive) {
        if (isActive) {
            return "-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 14px; -fx-cursor: hand; -fx-background-radius: 5;";
        } else {
            return "-fx-background-color: #34495e; -fx-text-fill: #bdc3c7; -fx-font-size: 14px; -fx-cursor: hand; -fx-background-radius: 5;";
        }
    }
}
