package cse311.gui.util;

import javafx.application.Platform;
import javafx.scene.control.TextArea;
import java.io.OutputStream;
import java.io.IOException;

public class GuiOutputStream extends OutputStream {
    private final TextArea outputArea;
    // Buffer removed as it was unused

    public GuiOutputStream(TextArea outputArea) {
        this.outputArea = outputArea;
    }

    @Override
    public void write(int b) throws IOException {
        char c = (char) b;
        // Batch updates slightly or just runLater per char?
        // Per char is safest for real-time smoothness but expensive.
        // Let's optimize slightly by appending to a buffer if running fast,
        // but for now simple runLater is robust.

        Platform.runLater(() -> {
            if (c == '\b') {
                // Handle Backspace: remove last character
                if (outputArea.getLength() > 0) {
                    outputArea.deleteText(outputArea.getLength() - 1, outputArea.getLength());
                }
            } else {
                outputArea.appendText(String.valueOf(c));
            }
        });
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        String s = new String(b, off, len);
        Platform.runLater(() -> {
            outputArea.appendText(s);
        });
    }
}
