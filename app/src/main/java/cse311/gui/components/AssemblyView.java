package cse311.gui.components;

import cse311.Disassembler;
import cse311.MemoryManager;
import cse311.RV32Cpu;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import cse311.kernel.process.Task;
import java.util.Map;

public class AssemblyView extends VBox {

    private final RV32Cpu cpu;
    private final MemoryManager memory;
    private final ListView<InstructionItem> listView;
    private final ObservableList<InstructionItem> instructions;
    private final Label taskLabel;
    private final CheckBox chkFollowPc;

    private int currentPc = 0;

    public AssemblyView(RV32Cpu cpu, MemoryManager memory) {
        this.cpu = cpu;
        this.memory = memory;
        this.instructions = FXCollections.observableArrayList();

        // Toolbar
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(5));
        toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        this.taskLabel = new Label("Task: Idle");
        this.taskLabel.setStyle("-fx-font-weight: bold;");

        this.chkFollowPc = new CheckBox("Follow PC");
        this.chkFollowPc.setSelected(true);

        toolbar.getChildren().addAll(taskLabel, new Separator(), chkFollowPc);

        // List View
        this.listView = new ListView<>(instructions);
        this.listView.setStyle("-fx-font-family: 'Monospace';");

        // Cell Factory for formatting
        listView.setCellFactory(param -> new ListCell<InstructionItem>() {
            @Override
            protected void updateItem(InstructionItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    setFont(Font.font("Monospace", 12));

                    if (item.isLabel) {
                        // Label Row: "00000060 <start>:"
                        setText(item.assembly);
                        setStyle("-fx-font-weight: bold; -fx-text-fill: black; -fx-background-color: transparent;");
                    } else {
                        // Instruction Row: " 60: 00058313 addi x6 x11 0"
                        setText(String.format("    %x:        %08x        %s", item.address, item.code, item.assembly));

                        if (item.address == currentPc) {
                            setStyle("-fx-background-color: #d4e157; -fx-text-fill: black;");
                        } else {
                            setStyle("-fx-background-color: transparent; -fx-text-fill: black;");
                        }
                    }
                }
            }
        });

        VBox.setVgrow(listView, Priority.ALWAYS);
        this.getChildren().addAll(toolbar, listView);
    }

    private int cachedStartAddress = -1;
    private int cachedEndAddress = -1;
    private static final int WINDOW_SIZE = 100; // Keep 100 instructions in buffer
    private static final int SCROLL_MARGIN = 20; // Reload if within 20 instructions of edge

    public void update() {
        // Run on JavaFX thread
        Platform.runLater(() -> {
            int pc = cpu.getProgramCounter();
            currentPc = pc;

            // Update Task Info
            Task task = cpu.getCurrentTask();
            String taskName = "Idle";
            int tid = 0;
            if (task != null) {
                tid = task.getId();
                taskName = task.getName();
            }
            taskLabel.setText(String.format("Task: [%d] %s", tid, taskName));

            listView.refresh();

            if (chkFollowPc.isSelected()) {
                boolean needsReload = false;

                if (instructions.isEmpty()) {
                    needsReload = true;
                } else {
                    if (pc < cachedStartAddress + (SCROLL_MARGIN * 4))
                        needsReload = true;
                    if (pc > cachedEndAddress - (SCROLL_MARGIN * 4))
                        needsReload = true;
                }

                if (needsReload) {
                    rebuildList(pc);
                }

                // Select and Scroll
                int index = -1;
                for (int i = 0; i < instructions.size(); i++) {
                    // We scroll to the instruction at the PC, not the label above it
                    if (!instructions.get(i).isLabel && instructions.get(i).address == pc) {
                        index = i;
                        break;
                    }
                }

                if (index != -1) {
                    listView.getSelectionModel().select(index);
                    listView.scrollTo(index - 10);
                }
            }
        });
    }

    private void rebuildList(int centerPc) {
        int startAddress = centerPc - ((WINDOW_SIZE / 2) * 4);
        if (startAddress < 0)
            startAddress = 0;

        cachedStartAddress = startAddress;

        // Retrieve Symbol Map
        Map<Integer, String> symbolMap = null;
        Task task = cpu.getCurrentTask();
        if (task != null && task.getProgramInfo() != null) {
            symbolMap = task.getProgramInfo().symbols;
        }

        instructions.clear();
        for (int i = 0; i < WINDOW_SIZE; i++) {
            int addr = startAddress + (i * 4);

            // Check for Symbol Label
            if (symbolMap != null && symbolMap.containsKey(addr)) {
                String symName = symbolMap.get(addr);
                // Ripes format: "00000060 <start>:"
                String labelStr = String.format("%08x <%s>:", addr, symName);
                instructions.add(new InstructionItem(addr, 0, labelStr, true));
            }

            try {
                int code = memory.readWord(addr);
                String asm = Disassembler.disassemble(code, addr);
                // Remove commas for clean look
                asm = asm.replace(",", "");

                instructions.add(new InstructionItem(addr, code, asm, false));
                cachedEndAddress = addr;
            } catch (Exception e) {
                instructions.add(new InstructionItem(addr, 0, "???", false));
                cachedEndAddress = addr;
            }
        }
    }

    public static class InstructionItem {
        public final int address;
        public final int code;
        public final String assembly;
        public final boolean isLabel;

        public InstructionItem(int address, int code, String assembly) {
            this(address, code, assembly, false);
        }

        public InstructionItem(int address, int code, String assembly, boolean isLabel) {
            this.address = address;
            this.code = code;
            this.assembly = assembly;
            this.isLabel = isLabel;
        }
    }
}
