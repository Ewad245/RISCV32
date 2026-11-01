package cse311.gui.components;

import cse311.MemoryManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("PMD.LooseCoupling")
public class HexMemoryView extends VBox {

    private final MemoryManager memory;
    private final ListView<Integer> listView;
    private volatile byte[] memorySnapshot;
    private final List<Integer> allAddresses;
    private volatile List<Integer> nonZeroAddresses;
    private final ObservableList<Integer> displayedItems;
    private boolean hideZeroRows = false;

    // Pre-computed hex lookup table for performance
    private static final String[] HEX_LOOKUP = new String[256];
    static {
        for (int i = 0; i < 256; i++) {
            HEX_LOOKUP[i] = String.format("%02X ", i);
        }
    }

    public HexMemoryView(MemoryManager memory) {
        this.memory = memory;
        this.setPadding(new Insets(10));
        this.setSpacing(10);

        // Toolbar with filter option
        CheckBox hideZeroCheckBox = new CheckBox("Hide Zero Rows");
        hideZeroCheckBox.setSelected(false);
        hideZeroCheckBox.setOnAction(e -> {
            hideZeroRows = hideZeroCheckBox.isSelected();
            applyFilter();
        });

        // Pre-calculate all row start addresses
        byte[] memData = memory.getByteMemory();
        memorySnapshot = memData.clone();
        int memSize = memData.length;

        // Jump to address controls
        Label lblJump = new Label("Jump:");
        TextField txtJump = new TextField();
        txtJump.setPromptText("Hex (e.g. 10000)");
        txtJump.setPrefWidth(120);

        Button btnJump = new Button("Go");
        Label lblJumpStatus = new Label();
        lblJumpStatus.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11px;");

        ComboBox<String> presetCombo = new ComboBox<>();
        presetCombo.getItems().addAll(
                "Presets...",
                "Code Base (0x00010000)",
                "Heap Start (0x00100000)",
                "Top of RAM (0x07FFF000)",
                "UART MMIO (0x10000000)",
                "Framebuffer (0x11000000)",
                "Stack (0x7FFFFFF0)"
        );
        presetCombo.setValue("Presets...");
        presetCombo.setOnAction(e -> {
            String val = presetCombo.getValue();
            if (val == null || "Presets...".equals(val)) return;
            long addr = 0;
            if (val.contains("0x00010000")) addr = 0x10000L;
            else if (val.contains("0x00100000")) addr = 0x100000L;
            else if (val.contains("0x07FFF000")) addr = 0x7FFF000L;
            else if (val.contains("0x10000000")) addr = 0x10000000L;
            else if (val.contains("0x11000000")) addr = 0x11000000L;
            else if (val.contains("0x7FFFFFF0")) addr = 0x7FFFFFF0L;
            txtJump.setText(String.format("0x%08X", addr));
            jumpToAddress(addr, lblJumpStatus, memSize);
        });

        Runnable doJump = () -> {
            String text = txtJump.getText();
            if (text == null || text.trim().isEmpty()) return;
            try {
                long addr = Long.parseUnsignedLong(text.trim().replaceAll("(?i)^0x", ""), 16);
                jumpToAddress(addr, lblJumpStatus, memSize);
            } catch (NumberFormatException ex) {
                lblJumpStatus.setText("Invalid hex");
            }
        };

        btnJump.setOnAction(e -> doJump.run());
        txtJump.setOnAction(e -> doJump.run());

        HBox toolbar = new HBox(10, hideZeroCheckBox, new Separator(Orientation.VERTICAL),
                lblJump, txtJump, btnJump, presetCombo, lblJumpStatus);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 0, 5, 0));

        Label header = new Label("Address      00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F   ASCII");
        header.setFont(Font.font("Monospaced", 14));

        listView = new ListView<>();
        listView.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 14;");
        VBox.setVgrow(listView, Priority.ALWAYS);

        int numRows = (memSize + 15) / 16;

        allAddresses = new ArrayList<>(numRows);
        for (int i = 0; i < memSize; i += 16) {
            allAddresses.add(i);
        }

        // Compute non-zero addresses initially
        nonZeroAddresses = computeNonZeroAddresses(memorySnapshot);

        displayedItems = FXCollections.observableArrayList(allAddresses);
        listView.setItems(displayedItems);

        // Use a cell factory to render the row content on demand (Virtualization)
        listView.setCellFactory(param -> new HexRowCell());

        this.getChildren().addAll(toolbar, header, listView);
    }

    public void update() {
        // Take snapshot and compute non-zero addresses in background
        Thread computeThread = new Thread(() -> {
            byte[] snapshot = memory.getByteMemory().clone();
            ArrayList<Integer> nonZero = (ArrayList<Integer>) computeNonZeroAddresses(snapshot);

            // Update on FX thread
            Platform.runLater(() -> {
                memorySnapshot = snapshot;
                nonZeroAddresses = nonZero;
                applyFilter();
            });
        });
        computeThread.setDaemon(true);
        computeThread.start();
    }

    private List<Integer> computeNonZeroAddresses(byte[] mem) {
        List<Integer> nonZero = new ArrayList<>();
        int memSize = mem.length;

        for (int addr = 0; addr < memSize; addr += 16) {
            if (!isRowAllZeros(addr, mem)) {
                nonZero.add(addr);
            }
        }
        return nonZero;
    }

    private void applyFilter() {
        List<Integer> newItems = hideZeroRows ? nonZeroAddresses : allAddresses;
        // Batch update: setAll is much faster than clear + addAll individually
        displayedItems.setAll(newItems);
    }

    private boolean isRowAllZeros(int address, byte[] mem) {
        int end = Math.min(address + 16, mem.length);
        for (int i = address; i < end; i++) {
            if (mem[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private class HexRowCell extends ListCell<Integer> {
        @Override
        protected void updateItem(Integer address, boolean empty) {
            super.updateItem(address, empty);
            if (empty || address == null) {
                setText(null);
            } else {
                setText(formatRow(address));
            }
        }
    }

    private String formatRow(int address) {
        StringBuilder sb = new StringBuilder(80);
        // Address: 8 chars hex
        sb.append(String.format("%08X:    ", address));

        byte[] mem = memorySnapshot;
        StringBuilder ascii = new StringBuilder(16);

        for (int i = 0; i < 16; i++) {
            int currentAddr = address + i;
            if (currentAddr < mem.length) {
                int b = mem[currentAddr] & 0xFF; // Unsigned byte
                sb.append(HEX_LOOKUP[b]);

                // Printable ASCII range
                char c = (b >= 32 && b <= 126) ? (char) b : '.';
                ascii.append(c);
            } else {
                sb.append("   ");
                ascii.append(" ");
            }
        }

        sb.append("  "); // Spacer
        sb.append(ascii);

        return sb.toString();
    }

    private void jumpToAddress(long address, Label statusLabel, int memSize) {
        if (address < 0) {
            statusLabel.setText("Invalid address");
            return;
        }
        if (address >= memSize) {
            statusLabel.setText(String.format("0x%08X is MMIO/Virtual (RAM: 0x0-0x%08X)", address, memSize));
            return;
        }
        statusLabel.setText("");
        int targetRow = (int) (address & ~0xF);
        int index;
        if (hideZeroRows) {
            index = Collections.binarySearch(displayedItems, targetRow);
            if (index < 0) {
                index = Math.max(0, -(index + 1) - 1);
            }
        } else {
            index = targetRow / 16;
        }
        if (index >= 0 && index < displayedItems.size()) {
            listView.scrollTo(index);
            listView.getSelectionModel().select(index);
        }
    }
}
