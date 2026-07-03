package cse311.gui.components;

import cse311.MemoryManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;

import com.techsenger.jeditermfx.ui.JediTermFxWidget;
import com.techsenger.jeditermfx.ui.settings.DefaultSettingsProvider;
import com.techsenger.jeditermfx.core.TtyConnector;

import java.io.IOException;

public class ConsoleView extends VBox {

    private JediTermFxWidget terminalWidget;

    public ConsoleView(MemoryManager memory) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ConsoleView.fxml"));
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load ConsoleView.fxml", e);
        }

        // Initialize JediTermFX Terminal with hardcoded 80 columns, 24 lines per user request
        terminalWidget = new JediTermFxWidget(80, 24, new DefaultSettingsProvider());
        
        VBox.setVgrow(terminalWidget.getPane(), Priority.ALWAYS);
        this.getChildren().add(terminalWidget.getPane());
    }

    public void setTtyConnector(TtyConnector connector) {
        terminalWidget.setTtyConnector(connector);
        terminalWidget.start();
    }

    // Deprecated: Keeping to avoid breaking Kernel or other parts calling this temporarily
    public void appendText(String text, String styleClass) {
        // Ignored. Use System.out
    }

    public void appendText(String text) {
        // Ignored. Use System.out
    }

    public void close() {
        if (terminalWidget != null) {
            terminalWidget.close();
        }
    }
}
