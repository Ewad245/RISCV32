package cse311.gui.components;

import cse311.MemoryManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.input.KeyEvent;
import org.fxmisc.richtext.StyleClassedTextArea;
import javafx.scene.layout.VBox;

import java.io.IOException;

public class ConsoleView extends VBox {

    @FXML
    private StyleClassedTextArea outputArea;

    public ConsoleView(MemoryManager memory) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ConsoleView.fxml"));
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load ConsoleView.fxml", e);
        }

        // Keep editable true so the cursor/caret is visible
        outputArea.setEditable(true);
        outputArea.setStyle("-fx-font-family: 'Courier New';");

        // Handle special keys via KEY_PRESSED (before KEY_TYPED)
        outputArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case BACK_SPACE:
                    memory.getInput("\b");
                    event.consume();
                    break;
                case DELETE:
                    event.consume();
                    break;
                case ENTER:
                    memory.getInput("\n");
                    event.consume();
                    break;
                default:
                    // Don't consume - let KEY_TYPED handle printable chars
                    break;
            }
        });

        // Handle regular character input via KEY_TYPED
        outputArea.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            String character = event.getCharacter();

            if (character == null || character.isEmpty()) {
                event.consume();
                return;
            }

            char c = character.charAt(0);

            // Skip control characters - they are handled by KEY_PRESSED above
            // Backspace (\b = 8), Tab (\t = 9), Enter/CR (\r = 13, \n = 10), etc.
            if (c < 32 || c == 127) {
                event.consume();
                return;
            }

            // Send printable character to memory
            memory.getInput(character);
            event.consume();
        });
    }

    /**
     * Appends text with a specific style class.
     * 
     * @param text       The text to append
     * @param styleClass The CSS style class to apply (e.g., "user-input",
     *                   "kernel-error")
     */
    public void appendText(String text, String styleClass) {
        int start = outputArea.getLength();
        outputArea.appendText(text);
        int end = outputArea.getLength();
        outputArea.setStyleClass(start, end, styleClass);
        outputArea.moveTo(outputArea.getLength());
    }

    /**
     * Appends plain text without styling.
     * 
     * @param text The text to append
     */
    public void appendText(String text) {
        outputArea.appendText(text);
        outputArea.moveTo(outputArea.getLength());
    }

    public StyleClassedTextArea getOutputArea() {
        return outputArea;
    }
}
