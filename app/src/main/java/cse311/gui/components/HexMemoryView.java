package cse311.gui.components;

import cse311.MemoryManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.util.ArrayList;

public class HexMemoryView extends VBox {

    private final MemoryManager memory;
    private final ListView<Integer> listView;

    public HexMemoryView(MemoryManager memory) {
        this.memory = memory;
        this.setPadding(new Insets(10));
        this.setSpacing(10);

        Label header = new Label("Address      00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F   ASCII");
        header.setFont(Font.font("Monospaced", 14));

        listView = new ListView<>();
        listView.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 14;");
        VBox.setVgrow(listView, Priority.ALWAYS);

        // Pre-calculate all row start addresses
        // 12MB is large, but creating 786k Integers is manageable for modern heap
        // (approx 12-16MB overhead)
        byte[] memData = memory.getByteMemory();
        int memSize = memData.length;
        int numRows = (memSize + 15) / 16;

        ArrayList<Integer> addresses = new ArrayList<>(numRows);
        for (int i = 0; i < memSize; i += 16) {
            addresses.add(i);
        }

        ObservableList<Integer> items = FXCollections.observableArrayList(addresses);
        listView.setItems(items);

        // Use a cell factory to render the row content on demand (Virtualization)
        listView.setCellFactory(param -> new HexRowCell());

        this.getChildren().addAll(header, listView);
    }

    public void update() {
        // Refresh visible cells to show new memory values
        listView.refresh();
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
        StringBuilder sb = new StringBuilder();
        // Address: 8 chars hex
        sb.append(String.format("%08X:    ", address));

        byte[] mem = memory.getByteMemory();
        StringBuilder ascii = new StringBuilder();

        for (int i = 0; i < 16; i++) {
            int currentAddr = address + i;
            if (currentAddr < mem.length) {
                byte b = mem[currentAddr];
                sb.append(String.format("%02X ", b));

                char c = (char) b;
                // Printable ASCII range (approx)
                if (c >= 32 && c <= 126) {
                    ascii.append(c);
                } else {
                    ascii.append('.');
                }
            } else {
                sb.append("   ");
                ascii.append(" ");
            }
        }

        sb.append("  "); // Spacer
        sb.append(ascii);

        return sb.toString();
    }
}
