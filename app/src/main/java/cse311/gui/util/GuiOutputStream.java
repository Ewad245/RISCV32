package cse311.gui.util;

import javafx.application.Platform;
import org.fxmisc.richtext.StyleClassedTextArea;
import java.io.OutputStream;
import java.io.IOException;

public class GuiOutputStream extends OutputStream {
    private final StyleClassedTextArea outputArea;
    private final String styleClass;

    /**
     * Creates a GuiOutputStream that writes to a StyleClassedTextArea.
     * 
     * @param outputArea The RichTextFX text area to write to
     */
    public GuiOutputStream(StyleClassedTextArea outputArea) {
        this(outputArea, "kernel-output");
    }

    /**
     * Creates a GuiOutputStream that writes to a StyleClassedTextArea with a
     * specific style.
     * 
     * @param outputArea The RichTextFX text area to write to
     * @param styleClass The CSS style class to apply to the text
     */
    public GuiOutputStream(StyleClassedTextArea outputArea, String styleClass) {
        this.outputArea = outputArea;
        this.styleClass = styleClass;
    }

    @Override
    public void write(int b) throws IOException {
        char c = (char) b;

        try {
            Platform.runLater(() -> {
                handleChar(c);
                scrollToEnd();
            });
        } catch (IllegalStateException e) {
            // Ignore: Toolkit is shut down, don't try to log to GUI anymore
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        String s = new String(b, off, len);
        Platform.runLater(() -> {
            for (int i = 0; i < s.length(); i++) {
                handleChar(s.charAt(i));
            }
            scrollToEnd();
        });
    }

    private void handleChar(char c) {
        if (c == '\b') {
            // Handle Backspace: remove last character
            if (outputArea.getLength() > 0) {
                outputArea.deleteText(outputArea.getLength() - 1, outputArea.getLength());
            }
        } else {
            int start = outputArea.getLength();
            outputArea.appendText(String.valueOf(c));
            int end = outputArea.getLength();
            outputArea.setStyleClass(start, end, styleClass);
        }
    }

    private void scrollToEnd() {
        // Move caret to end and scroll to make it visible
        outputArea.moveTo(outputArea.getLength());
        outputArea.requestFollowCaret();
    }
}
