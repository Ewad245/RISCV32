package cse311.gui.components;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.function.Consumer;

@SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.AvoidDuplicateLiterals"})
public class SidebarView extends VBox {

    private final Consumer<String> onNavigation;

    @FXML
    private Button btnDashboard;
    @FXML
    private Button btnDatapath;
    @FXML
    private Button btnMemory;

    public SidebarView(Consumer<String> onNavigation) {
        this.onNavigation = onNavigation;

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/SidebarView.fxml"));
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load SidebarView.fxml", e);
        }

        // Set initial active state
        updateActiveState(btnDashboard);
    }

    @FXML
    private void onDashboardClicked() {
        updateActiveState(btnDashboard);
        onNavigation.accept("dashboard");
    }

    @FXML
    private void onDatapathClicked() {
        updateActiveState(btnDatapath);
        onNavigation.accept("datapath");
    }

    @FXML
    private void onMemoryClicked() {
        updateActiveState(btnMemory);
        onNavigation.accept("memory");
    }

    private void updateActiveState(Button activeBtn) {
        // Remove active class from all
        btnDashboard.getStyleClass().remove("nav-button-active");
        btnDatapath.getStyleClass().remove("nav-button-active");
        btnMemory.getStyleClass().remove("nav-button-active");

        // Add active class to selected
        activeBtn.getStyleClass().add("nav-button-active");
    }
}
