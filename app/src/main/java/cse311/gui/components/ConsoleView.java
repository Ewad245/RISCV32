package cse311.gui.components;

import cse311.MemoryManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

import java.io.IOException;

public class ConsoleView extends VBox {

    @FXML
    private TextArea outputArea;

    public ConsoleView(MemoryManager memory) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ConsoleView.fxml"));
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load ConsoleView.fxml", e);
        }

        // Logic for Input (Simulated Keyboard)
        outputArea.setOnKeyTyped(event -> {
            String character = event.getCharacter();
            if (character.equals("\r")) {
                character = "\n";
            }
            if (character != null && !character.isEmpty()) {
                memory.getInput(character);
            }
        });
    }

    public TextArea getOutputArea() {
        return outputArea;
    }
}
