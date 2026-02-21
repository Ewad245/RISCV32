package cse311.gui.components;

import cse311.Disassembler;
import cse311.MemoryManager;
import cse311.RV32Cpu;
import cse311.kernel.process.Task;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.io.IOException;
import java.util.Map;

public class AssemblyView extends VBox {

    private RV32Cpu cpu;
    private final MemoryManager memory;

    public void setCpu(RV32Cpu cpu) {
        this.cpu = cpu;
        this.cachedInstructionsPid = -1; // Force refresh
        this.currentPc = -1;
        this.instructions.clear();
        this.addressToIndexMap.clear();
        update();
    }

    @FXML
    private ListView<InstructionItem> listView;
    @FXML
    private Label taskLabel;
    @FXML
    private CheckBox chkFollowPc;

    private final ObservableList<InstructionItem> instructions;
    private int currentPc = 0;
    private int cachedInstructionsPid = -1;
    private final Map<Integer, Integer> addressToIndexMap = new java.util.HashMap<>();
    private int animationPreviousPc = -1;

    public AssemblyView(RV32Cpu cpu, MemoryManager memory) {
        this.cpu = cpu;
        this.memory = memory;
        this.instructions = FXCollections.observableArrayList();

        // 1. Load FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AssemblyView.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load AssemblyView.fxml", e);
        }

        // 2. Configure List View (Logic stays in Java)
        listView.setItems(instructions);
        listView.setCellFactory(param -> new InstructionCell());
    }

    // --- Inner Class for Cell Logic (Stays in Java) ---
    private class InstructionCell extends ListCell<InstructionItem> {
        public InstructionCell() {
            setOnMouseClicked(e -> {
                if (getItem() != null && !isEmpty()) {
                    cpu.toggleBreakpoint(getItem().address);
                    listView.refresh();
                }
            });
        }

        @Override
        protected void updateItem(InstructionItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setStyle("-fx-background-color: transparent;");
            } else {
                setFont(Font.font("Monospace", 12));

                // Breakpoint Dot
                if (cpu.hasBreakpoint(item.address)) {
                    setGraphic(new javafx.scene.shape.Circle(5, javafx.scene.paint.Color.RED));
                } else {
                    setGraphic(null);
                }

                // Text & Color Logic
                if (item.isLabel) {
                    setText(item.assembly);
                    setStyle("-fx-font-weight: bold; -fx-text-fill: black; -fx-background-color: transparent;");
                } else {
                    // Use 4-digit hex for compressed (16-bit) instructions, 8-digit for standard
                    String hexCode = (item.code <= 0xFFFF && item.code >= 0)
                            ? String.format("%04x", item.code)
                            : String.format("%08x", item.code);
                    setText(String.format("    %x:        %s        %s", item.address, hexCode, item.assembly));

                    if (item.address == currentPc) {
                        setStyle("-fx-background-color: #d4e157; -fx-text-fill: black;"); // Highlight
                    } else if (item.address == animationPreviousPc) {
                        setStyle("-fx-background-color: #f0f4c3; -fx-text-fill: black;"); // Trail
                    } else {
                        setStyle("-fx-background-color: transparent; -fx-text-fill: black;");
                    }
                }
            }
        }
    }

    public void update() {
        // Run on JavaFX thread
        Platform.runLater(() -> {
            int pc = cpu.getLastPC();
            // If execution hasn't started (lastPC = -1), fallback to next PC to show entry
            // point
            if (pc == -1) {
                pc = cpu.getProgramCounter();
            }

            // Update History logic
            if (pc != currentPc) {
                animationPreviousPc = currentPc;
            }
            currentPc = pc;

            // Update Task Info
            Task task = cpu.getCurrentTask();
            String taskName = "Idle";
            int tid = 0;

            if (task == null) {
                taskLabel.setText("Task: Idle");
                // If idle, we switch to a special "Idle" state display
                if (cachedInstructionsPid != 0) {
                    instructions.clear();
                    addressToIndexMap.clear();
                    instructions.add(new InstructionItem(0, 0, "CPU is not running", false));
                    cachedInstructionsPid = 0;
                }
            } else {
                tid = task.getId();
                taskName = task.getName();
                taskLabel.setText(String.format("Task: [%d] %s", tid, taskName));

                // Rebuild list only if Task ID changed
                if (cachedInstructionsPid != tid) {
                    rebuildFullList(task);
                    cachedInstructionsPid = tid;
                }
            }

            listView.refresh(); // Refresh to update colors (Current vs Previous vs Breakpoints)

            if (task != null && chkFollowPc.isSelected()) {
                // Select and Scroll
                Integer index = addressToIndexMap.get(pc); // O(1) Lookup

                if (index != null) {
                    listView.getSelectionModel().select(index);
                    listView.scrollTo(index - 5); // Center comfortably
                } else {
                    listView.getSelectionModel().clearSelection();
                }
            }
        });
    }

    private void rebuildFullList(Task task) {
        instructions.clear();
        addressToIndexMap.clear();

        if (task.getProgramInfo() == null) {
            instructions.add(new InstructionItem(0, 0, "No Program Info Available", false));
            return;
        }

        int start = task.getProgramInfo().textStart;
        int size = task.getProgramInfo().textSize;
        // Safety cap: don't disassembly more than 1MB of code for GUI to avoid hang
        if (size > 1024 * 1024)
            size = 1024 * 1024;

        int end = start + size;
        Map<Integer, String> symbolMap = task.getProgramInfo().symbols;
        int pid = task.getId();

        int listIndex = 0;

        int addr = start;
        while (addr < end) {
            // Check for Symbol Label
            if (symbolMap != null && symbolMap.containsKey(addr)) {
                String symName = symbolMap.get(addr);
                String labelStr = String.format("%08x <%s>:", addr, symName);
                instructions.add(new InstructionItem(addr, 0, labelStr, true));
                listIndex++;
            }

            try {
                // Read lower 16 bits first to check for compressed instruction
                int lower16 = memory.debugReadWord(addr, pid) & 0xFFFF;

                String asm;
                int code;
                int step;

                if (lower16 == 0) {
                    // Zero instruction
                    code = memory.debugReadWord(addr, pid);
                    asm = ".word 0";
                    step = (code == 0) ? 4 : 4; // If whole word is 0, step 4
                    // But check if only lower half is 0 (compressed NOP-like)
                    if ((lower16 & 0x3) != 0x3) {
                        step = 2;
                        code = lower16;
                    }
                } else if ((lower16 & 0x3) != 0x3) {
                    // Compressed (16-bit) instruction
                    code = lower16;
                    asm = Disassembler.disassembleCompressed(lower16, addr);
                    step = 2;
                } else {
                    // Standard (32-bit) instruction
                    code = memory.debugReadWord(addr, pid);
                    asm = Disassembler.disassemble(code, addr);
                    step = 4;
                }

                asm = asm.replace(",", "");

                if (asm.contains("ecall")) {
                    asm += "  <-- Ecall java side is running";
                }

                instructions.add(new InstructionItem(addr, code, asm, false));

                // Map Address -> List Index
                addressToIndexMap.put(addr, listIndex);
                listIndex++;
                addr += step;

            } catch (Exception e) {
                instructions.add(new InstructionItem(addr, 0, "???", false));
                listIndex++;
                addr += 2; // Step by 2 on error to avoid skipping compressed instructions
            }
        }
    }

    public static class InstructionItem {
        public final int address;
        public final int code;
        public final String assembly;
        public final boolean isLabel;

        public InstructionItem(int address, int code, String assembly, boolean isLabel) {
            this.address = address;
            this.code = code;
            this.assembly = assembly;
            this.isLabel = isLabel;
        }
    }
}
