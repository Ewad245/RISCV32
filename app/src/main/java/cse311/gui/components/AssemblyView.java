package cse311.gui.components;

import cse311.Disassembler;
import cse311.MemoryManager;
import cse311.RV32Cpu;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import cse311.kernel.process.Task;

public class AssemblyView extends VBox {

    private final RV32Cpu cpu;
    private final MemoryManager memory;
    private final ListView<InstructionItem> listView;
    private final ObservableList<InstructionItem> instructions;
    private final Label taskLabel;

    public AssemblyView(RV32Cpu cpu, MemoryManager memory) {
        this.cpu = cpu;
        this.memory = memory;
        this.instructions = FXCollections.observableArrayList();

        this.taskLabel = new Label("Task: Idle");
        this.taskLabel.setStyle("-fx-font-weight: bold; -fx-padding: 5;");

        this.listView = new ListView<>(instructions);
        this.listView.setCellFactory(param -> new ListCell<InstructionItem>() {
            @Override
            protected void updateItem(InstructionItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    setText(item.toString());
                    setFont(Font.font("Courier New", 12));
                    if (item.isCurrentPc) {
                        setStyle("-fx-background-color: #e0f7fa; -fx-text-fill: #006064; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-background-color: transparent; -fx-text-fill: black;");
                    }
                }
            }
        });

        VBox.setVgrow(listView, Priority.ALWAYS);
        this.getChildren().addAll(taskLabel, listView);
    }

    private int lastPc = -1;

    public void update() {
        // Run on JavaFX thread
        Platform.runLater(() -> {
            int pc = cpu.getProgramCounter();

            // Only update if PC changed, preventing scroll lock
            if (pc == lastPc && !instructions.isEmpty()) {
                return;
            }
            lastPc = pc;
            // Update Task Info
            Task task = cpu.getCurrentTask();
            if (task != null) {
                taskLabel.setText(String.format("Task: [%d] %s", task.getId(), task.getName()));
            } else {
                taskLabel.setText("Task: [0] Idle");
            }

            // int pc = cpu.getProgramCounter(); // Removed duplicate
            instructions.clear();

            // Show 15 instructions before and 15 after (total 31)
            int startAddress = pc - (15 * 4);
            if (startAddress < 0)
                startAddress = 0;

            for (int i = 0; i < 31; i++) {
                int addr = startAddress + (i * 4);
                try {
                    int instruction = memory.readWord(addr);
                    String asm = Disassembler.disassemble(instruction, addr);
                    boolean isCurrent = (addr == pc);
                    instructions.add(new InstructionItem(addr, instruction, asm, isCurrent));
                } catch (Exception e) {
                    instructions.add(new InstructionItem(addr, 0, "INVALID ACCESS", false));
                }
            }

            // Auto scroll to center
            listView.scrollTo(15);
        });
    }

    private static class InstructionItem {
        final int address;
        final int code;
        final String assembly;
        final boolean isCurrentPc;

        public InstructionItem(int address, int code, String assembly, boolean isCurrentPc) {
            this.address = address;
            this.code = code;
            this.assembly = assembly;
            this.isCurrentPc = isCurrentPc;
        }

        @Override
        public String toString() {
            return String.format("[0x%08X]  %08X  %s", address, code, assembly);
        }
    }
}
