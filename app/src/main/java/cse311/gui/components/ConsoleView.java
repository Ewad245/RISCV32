package cse311.gui.components;

import cse311.MemoryManager;
import javafx.geometry.Insets;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;

/**
 * A Console View that displays UART output and captures input.
 */
public class ConsoleView extends VBox {

    private final TextArea outputArea;
    // Removed unused memory field
    // private final MemoryManager memory;

    public ConsoleView(MemoryManager memory) {
        // this.memory = memory;

        this.setPadding(new Insets(5));

        outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea
                .setStyle("-fx-font-family: 'Courier New'; -fx-control-inner-background: black; -fx-text-fill: white;");
        outputArea.setWrapText(true);

        // Capture key events for input (Simulated Keyboard)
        // Capture key typed events for input (Simulated Keyboard)
        // We use setOnKeyTyped because it handles Shift modifiers and special
        // characters correctly
        outputArea.setOnKeyTyped(event -> {
            String character = event.getCharacter();

            // Handle special mappings if necessary
            // JavaFX weirdness: Enter might be \r, we might want to normalize to \n or just
            // pass it
            if (character.equals("\r")) {
                character = "\n";
            }

            // Check if text is valid character
            if (character != null && !character.isEmpty()) {
                // Send to Simulated UART
                memory.getInput(character);
            }
        });

        VBox.setVgrow(outputArea, Priority.ALWAYS);
        this.getChildren().add(outputArea);

        // Register Output Listener
        // REMOVED: We now redirect System.out globally in MainController
        // memory.getUart().setOutputListener(this::appendChar);
    }

    public TextArea getOutputArea() {
        return outputArea;
    }
}
