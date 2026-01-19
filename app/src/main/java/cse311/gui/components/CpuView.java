package cse311.gui.components;

import cse311.RV32Cpu;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;

// Extend the container type used in fx:root
public class CpuView extends VBox {

    private final RV32Cpu cpu;

    @FXML
    private Label lblPc;
    @FXML
    private GridPane regGrid;

    private final Label[] nameLabels = new Label[32];
    private final Label[] valLabels = new Label[32];

    // Width of one name+value pair (Name: 40px, Gap: 5px, Value: 80px, Gap: 15px)
    // Approximate, or we can use constraints.
    private static final double ITEM_WIDTH = 150.0;
    private int currentColumnCount = 0;

    public CpuView(RV32Cpu cpu) {
        this.cpu = cpu;

        // 1. Load FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/CpuView.fxml"));
        loader.setRoot(this); // Sets 'this' as the root of the FXML
        loader.setController(this); // Sets 'this' as the controller

        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load CpuView.fxml", e);
        }

        // 2. Initialize Labels
        initializeLabels();

        // 3. Responsive Listener
        this.widthProperty().addListener((obs, oldVal, newVal) -> {
            updateLayout(newVal.doubleValue());
        });

        // Initial layout (might need Platform.runLater if width is 0 initially)
        updateLayout(this.getWidth());
        update();
    }

    private void initializeLabels() {
        String[] names = {
                "zero", "ra", "sp", "gp", "tp", "t0", "t1", "t2",
                "s0/fp", "s1", "a0", "a1", "a2", "a3", "a4", "a5",
                "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7",
                "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"
        };

        for (int i = 0; i < 32; i++) {
            Label nameLbl = new Label(names[i] + ":");
            nameLbl.getStyleClass().add("reg-name");
            nameLabels[i] = nameLbl;

            Label valLbl = new Label("0x00000000");
            valLbl.getStyleClass().add("reg-value");
            valLabels[i] = valLbl;
        }
    }

    private void updateLayout(double width) {
        if (width <= 0)
            return;

        double usableWidth = width - getPadding().getLeft() - getPadding().getRight();

        // How many *name-value pairs* fit ≈
        int pairsPerRow = Math.max(1, (int) (usableWidth / 220)); // wider because more relaxed

        // But we can also consider height if you want (more advanced)
        double approxHeightPerRow = 28; // guess
        int approxVisibleRows = (int) (this.getHeight() / approxHeightPerRow);

        // Option A: only care about width (simplest)
        int targetColumns = pairsPerRow * 2; // name + value = 2 grid columns

        // Option B: try to make roughly square-ish if height is known
        // int targetColumns = (int) Math.sqrt(64.0 * (width / getHeight()));

        if (targetColumns != currentColumnCount) {
            currentColumnCount = targetColumns;
            rebuildAsFullGrid(targetColumns);
        }
    }

    private void rebuildAsFullGrid(int numColumns) {
        regGrid.getChildren().clear();
        regGrid.getColumnConstraints().clear();

        // Create as many columns as decided
        for (int c = 0; c < numColumns; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setMinWidth((c % 2 == 0) ? 50 : 110); // name narrower, value wider
            cc.setHgrow((c % 2 == 0) ? Priority.NEVER : Priority.ALWAYS);
            regGrid.getColumnConstraints().add(cc);
        }

        // Place all 64 labels in row-major order
        for (int i = 0; i < 32; i++) {
            int idx = i * 2;
            int row = idx / numColumns;
            int colName = idx % numColumns;
            int colValue = (colName + 1) % numColumns;

            // If next column would wrap → put value on next row
            if (colValue < colName) {
                row++;
                colValue = 0;
            }

            regGrid.add(nameLabels[i], colName, row);
            regGrid.add(valLabels[i], colValue, row);
        }
    }

    public void update() {
        if (cpu == null)
            return;

        lblPc.setText(String.format("PC: 0x%08X", cpu.getProgramCounter()));

        int[] regs = cpu.getRegisters();
        for (int i = 0; i < 32; i++) {
            valLabels[i].setText(String.format("0x%08X", regs[i]));

            if (i == 2) { // Stack Pointer (x2)
                if (!valLabels[i].getStyleClass().contains("stack-pointer")) {
                    valLabels[i].getStyleClass().add("stack-pointer");
                }
            }
        }
    }
}
