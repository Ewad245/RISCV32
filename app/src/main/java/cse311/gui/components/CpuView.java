package cse311.gui.components;

import cse311.RV32Cpu;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

public class CpuView extends VBox {

    private final RV32Cpu cpu;
    private final Label[] regLabels = new Label[32];
    private final Label lblPc;

    public CpuView(RV32Cpu cpu) {
        this.cpu = cpu;
        this.setPadding(new Insets(10));
        this.setStyle("-fx-border-color: #ccc; -fx-background-color: #f9f9f9;");

        lblPc = new Label("PC: 0x00000000");
        lblPc.setStyle("-fx-font-family: 'Courier New'; -fx-font-weight: bold; -fx-font-size: 14px;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(5);

        for (int i = 0; i < 32; i++) {
            String name = getRegName(i);
            Label lblName = new Label(name + ":");
            lblName.setStyle("-fx-font-family: 'Courier New'; -fx-font-weight: bold;");

            Label lblVal = new Label("0x00000000");
            lblVal.setStyle("-fx-font-family: 'Courier New';");

            regLabels[i] = lblVal;

            grid.add(lblName, (i % 4) * 2, i / 4);
            grid.add(lblVal, (i % 4) * 2 + 1, i / 4);
        }

        this.getChildren().addAll(lblPc, grid);
    }

    public void update() {
        if (cpu == null)
            return;

        lblPc.setText(String.format("PC: 0x%08X", cpu.getProgramCounter()));

        int[] regs = cpu.getRegisters();
        for (int i = 0; i < 32; i++) {
            regLabels[i].setText(String.format("0x%08X", regs[i]));
        }
    }

    private String getRegName(int i) {
        String[] names = {
                "zero", "ra", "sp", "gp", "tp", "t0", "t1", "t2",
                "s0/fp", "s1", "a0", "a1", "a2", "a3", "a4", "a5",
                "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7",
                "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"
        };
        return names[i];
    }
}
