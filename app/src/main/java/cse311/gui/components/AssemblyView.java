package cse311.gui.components;

import cse311.Disassembler;
import cse311.MemoryManager;
import cse311.RV32Cpu;
import cse311.kernel.Kernel;
import cse311.kernel.process.ProgramInfo;
import cse311.kernel.process.Task;
import cse311.pipeline.ExecutionTraceBuffer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.io.IOException;
import java.util.*;

@SuppressWarnings({
    "PMD.AvoidLiteralsInIfCondition",
    "PMD.AvoidCatchingGenericException",
    "PMD.IdenticalConditionalBranches",
    "PMD.UnusedPrivateMethod"
})
public class AssemblyView extends VBox {

    public enum ScopeType {
        HART,
        TASK,
        ALL_HARTS
    }

    public static class TargetScope {
        public final ScopeType type;
        public final int id; // hartId or pid
        public final String label;

        public TargetScope(ScopeType type, int id, String label) {
            this.type = type;
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TargetScope that)) return false;
            return id == that.id && type == that.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, id);
        }
    }

    private RV32Cpu cpu;
    private Kernel kernel;
    private final MemoryManager memory;

    @FXML private ComboBox<TargetScope> cmbTarget;
    @FXML private Label taskLabel;
    @FXML private ToggleButton btnModeDisasm;
    @FXML private ToggleButton btnModeTrace;
    @FXML private ToggleButton btnModeSplit;
    @FXML private ToggleGroup viewModeGroup;
    @FXML private CheckBox chkFollowPc;
    @FXML private CheckBox chkUserOnly;
    @FXML private Button btnClearTrace;
    @FXML private SplitPane contentSplit;
    @FXML private ListView<InstructionItem> listView;

    private VBox traceContainer;
    private ListView<String> traceListView;
    private final ObservableList<String> traceItems = FXCollections.observableArrayList();
    private final ObservableList<InstructionItem> instructions = FXCollections.observableArrayList();

    private final Map<Integer, Integer> addressToIndexMap = new HashMap<>();
    private int currentPc = 0;
    private int animationPreviousPc = -1;
    private int cachedInstructionsPid = -1;
    private TargetScope activeScope;
    private int lastKnownTaskCount = -1;

    public AssemblyView(RV32Cpu cpu, MemoryManager memory) {
        this(null, cpu, memory);
    }

    public AssemblyView(Kernel kernel, RV32Cpu cpu, MemoryManager memory) {
        this.kernel = kernel;
        this.cpu = cpu;
        this.memory = memory;

        // 1. Load FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AssemblyView.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load AssemblyView.fxml", e);
        }

        // 2. Setup Trace Container & List
        setupTraceView();

        // 3. Configure Disassembly List View
        listView.setItems(instructions);
        listView.setCellFactory(param -> new InstructionCell());

        // 4. Configure Scope Selector & Mode Switching
        setupControls();

        // Initial view mode: Disassembly only
        updateViewMode();
    }

    public void setKernel(Kernel kernel) {
        this.kernel = kernel;
        refreshScopeItems();
    }

    public void setCpu(RV32Cpu cpu) {
        this.cpu = cpu;
        this.cachedInstructionsPid = -1;
        this.currentPc = -1;
        this.instructions.clear();
        this.addressToIndexMap.clear();
        refreshScopeItems();
        update();
    }

    private void setupTraceView() {
        traceContainer = new VBox(4);
        traceContainer.setPadding(new Insets(4));
        traceContainer.setStyle("-fx-background-color: #0f172a;");

        HBox traceHeader = new HBox(8);
        traceHeader.setPadding(new Insets(2, 6, 2, 6));
        Label lblTraceTitle = new Label("Execution History (Chronological Log)");
        lblTraceTitle.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");
        traceHeader.getChildren().add(lblTraceTitle);

        traceListView = new ListView<>(traceItems);
        traceListView.setStyle("-fx-font-family: 'Monospace'; -fx-control-inner-background: #0f172a; -fx-background-color: #0f172a;");
        VBox.setVgrow(traceListView, Priority.ALWAYS);

        traceListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    setFont(Font.font("Monospace", 11));
                    setText(item);
                    if (item.contains("Hart 0")) {
                        setStyle("-fx-text-fill: #38bdf8; -fx-background-color: transparent;");
                    } else if (item.contains("Hart 1")) {
                        setStyle("-fx-text-fill: #c084fc; -fx-background-color: transparent;");
                    } else if (item.contains("amo") || item.contains("lr.") || item.contains("sc.")) {
                        setStyle("-fx-text-fill: #f472b6; -fx-font-weight: bold; -fx-background-color: transparent;");
                    } else {
                        setStyle("-fx-text-fill: #e2e8f0; -fx-background-color: transparent;");
                    }
                }
            }
        });

        traceContainer.getChildren().addAll(traceHeader, traceListView);
    }

    private void setupControls() {
        int initialHartId = (cpu != null) ? cpu.getId() : 0;
        activeScope = new TargetScope(ScopeType.HART, initialHartId, "Hart " + initialHartId + " (Core)");

        refreshScopeItems();

        cmbTarget.setOnAction(e -> {
            TargetScope sel = cmbTarget.getSelectionModel().getSelectedItem();
            if (sel != null && !sel.equals(activeScope)) {
                activeScope = sel;
                cachedInstructionsPid = -1; // Force disassembly refresh
                currentPc = -1;
                update();
            }
        });

        viewModeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null && oldVal != null) {
                oldVal.setSelected(true);
                return;
            }
            updateViewMode();
        });
    }

    private void updateViewMode() {
        if (contentSplit == null) return;
        if (btnModeDisasm.isSelected()) {
            contentSplit.getItems().setAll(listView);
        } else if (btnModeTrace.isSelected()) {
            contentSplit.getItems().setAll(traceContainer);
        } else if (btnModeSplit.isSelected()) {
            contentSplit.getItems().setAll(listView, traceContainer);
            contentSplit.setDividerPositions(0.5);
        }
    }

    public void setTargetScope(ScopeType type, int id) {
        if (cmbTarget == null) return;
        for (TargetScope s : cmbTarget.getItems()) {
            if (s.type == type && (type == ScopeType.ALL_HARTS || s.id == id)) {
                cmbTarget.getSelectionModel().select(s);
                activeScope = s;
                cachedInstructionsPid = -1;
                currentPc = -1;
                update();
                break;
            }
        }
    }

    public void setViewMode(String mode) {
        if ("trace".equalsIgnoreCase(mode)) {
            if (btnModeTrace != null) btnModeTrace.setSelected(true);
        } else if ("split".equalsIgnoreCase(mode)) {
            if (btnModeSplit != null) btnModeSplit.setSelected(true);
        } else {
            if (btnModeDisasm != null) btnModeDisasm.setSelected(true);
        }
        updateViewMode();
    }

    @FXML
    private void onClearTraceClicked() {
        if (cpu != null && cpu.getTraceBuffer() != null) {
            cpu.getTraceBuffer().clear();
        }
        if (kernel != null) {
            for (int i = 0; i < kernel.getConfig().getCoreCount(); i++) {
                kernel.getCpu(i).getTraceBuffer().clear();
            }
        }
        traceItems.clear();
    }

    private void refreshScopeItems() {
        if (cmbTarget == null) return;

        List<TargetScope> items = new ArrayList<>();
        int hartId = (cpu != null) ? cpu.getId() : 0;
        TargetScope defaultHart = new TargetScope(ScopeType.HART, hartId, "Hart " + hartId + " (Core)");
        items.add(defaultHart);

        if (kernel != null) {
            int coreCount = kernel.getConfig().getCoreCount();
            if (coreCount > 1) {
                items.add(new TargetScope(ScopeType.ALL_HARTS, -1, "All Harts (System Trace)"));
            }

            Collection<Task> tasks = kernel.getAllTasks();
            if (tasks != null) {
                for (Task t : tasks) {
                    items.add(new TargetScope(ScopeType.TASK, t.getId(), String.format("PID %d: %s", t.getId(), t.getName())));
                }
            }
        }

        // Update items in ComboBox preserving selection
        TargetScope currentSel = cmbTarget.getSelectionModel().getSelectedItem();
        if (currentSel == null) {
            currentSel = activeScope != null ? activeScope : defaultHart;
        }

        cmbTarget.setItems(FXCollections.observableArrayList(items));

        TargetScope toSelect = defaultHart;
        for (TargetScope s : items) {
            if (s.equals(currentSel)) {
                toSelect = s;
                break;
            }
        }
        cmbTarget.getSelectionModel().select(toSelect);
        activeScope = toSelect;
    }

    public void update() {
        if (kernel != null && kernel.getAllTasks() != null) {
            int currentTaskCount = kernel.getAllTasks().size();
            if (currentTaskCount != lastKnownTaskCount) {
                lastKnownTaskCount = currentTaskCount;
                Platform.runLater(this::refreshScopeItems);
            }
        }

        if (activeScope == null) {
            int defaultId = (cpu != null) ? cpu.getId() : 0;
            activeScope = new TargetScope(ScopeType.HART, defaultId, "Hart " + defaultId + " (Core)");
        }

        int targetPc = -1;
        Task targetTask = null;
        String statusText = "Idle";
        String statusStyle = "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-padding: 2 6 2 6; -fx-background-radius: 4;";

        // 1. Resolve Target State according to Active Scope
        if (activeScope.type == ScopeType.HART) {
            RV32Cpu targetCpu = (kernel != null) ? kernel.getCpu(activeScope.id) : cpu;
            if (targetCpu != null) {
                targetPc = targetCpu.getLastPC();
                if (targetPc == -1) {
                    targetPc = targetCpu.getProgramCounter();
                }
                targetTask = targetCpu.getCurrentTask();
                if (targetTask != null) {
                    statusText = String.format("Hart %d [PID %d: %s]", targetCpu.getId(), targetTask.getId(), targetTask.getName());
                    statusStyle = "-fx-background-color: #dcfce7; -fx-text-fill: #15803d; -fx-padding: 2 6 2 6; -fx-background-radius: 4; -fx-font-weight: bold;";
                } else {
                    statusText = String.format("Hart %d [Idle]", targetCpu.getId());
                }
            }
        } else if (activeScope.type == ScopeType.TASK) {
            targetTask = findTask(activeScope.id);
            if (targetTask != null) {
                RV32Cpu runningCpu = findCpuRunningTask(targetTask);
                if (runningCpu != null) {
                    targetPc = runningCpu.getLastPC();
                    if (targetPc == -1) {
                        targetPc = runningCpu.getProgramCounter();
                    }
                    statusText = String.format("Hart %d [PID %d - RUNNING]", runningCpu.getId(), targetTask.getId());
                    statusStyle = "-fx-background-color: #dcfce7; -fx-text-fill: #15803d; -fx-padding: 2 6 2 6; -fx-background-radius: 4; -fx-font-weight: bold;";
                } else {
                    targetPc = targetTask.getProgramCounter();
                    statusText = String.format("PID %d [%s]", targetTask.getId(), targetTask.getState());
                    if (targetTask.getState() == cse311.kernel.process.TaskState.READY) {
                        statusStyle = "-fx-background-color: #fef3c7; -fx-text-fill: #b45309; -fx-padding: 2 6 2 6; -fx-background-radius: 4; -fx-font-weight: bold;";
                    } else if (targetTask.getState() == cse311.kernel.process.TaskState.WAITING) {
                        statusStyle = "-fx-background-color: #e0f2fe; -fx-text-fill: #0369a1; -fx-padding: 2 6 2 6; -fx-background-radius: 4; -fx-font-weight: bold;";
                    } else {
                        statusStyle = "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-padding: 2 6 2 6; -fx-background-radius: 4;";
                    }
                }
            } else {
                statusText = String.format("PID %d [Exited]", activeScope.id);
            }
        } else if (activeScope.type == ScopeType.ALL_HARTS) {
            int coreCount = (kernel != null) ? kernel.getConfig().getCoreCount() : 1;
            statusText = String.format("All Harts (%d Cores)", coreCount);
            statusStyle = "-fx-background-color: #ede9fe; -fx-text-fill: #6d28d9; -fx-padding: 2 6 2 6; -fx-background-radius: 4; -fx-font-weight: bold;";
            RV32Cpu bsp = (kernel != null) ? kernel.getCpu(0) : cpu;
            if (bsp != null) {
                targetPc = bsp.getLastPC();
                if (targetPc == -1) targetPc = bsp.getProgramCounter();
                targetTask = bsp.getCurrentTask();
            }
        }

        final int finalPc = targetPc;
        final Task finalTask = targetTask;
        final String finalStatusText = statusText;
        final String finalStatusStyle = statusStyle;

        Platform.runLater(() -> {
            taskLabel.setText(finalStatusText);
            taskLabel.setStyle(finalStatusStyle);

            if (finalPc != currentPc) {
                animationPreviousPc = currentPc;
            }
            currentPc = finalPc;

            // Rebuild disassembly only if PID changed
            int targetPid = (finalTask != null) ? finalTask.getId() : 0;
            if (cachedInstructionsPid != targetPid) {
                if (finalTask != null) {
                    rebuildFullList(finalTask);
                    cachedInstructionsPid = targetPid;
                } else if (activeScope.type == ScopeType.HART) {
                    instructions.clear();
                    addressToIndexMap.clear();
                    instructions.add(new InstructionItem(0, 0, "No active task on this Hart", false));
                    cachedInstructionsPid = 0;
                }
            }

            listView.refresh();

            // Auto-scroll to active PC if Follow PC is selected
            if (chkFollowPc != null && chkFollowPc.isSelected() && !btnModeTrace.isSelected()) {
                Integer index = addressToIndexMap.get(finalPc);
                if (index != null) {
                    listView.getSelectionModel().select(index.intValue());
                    listView.scrollTo(Math.max(0, index.intValue() - 5));
                } else {
                    listView.getSelectionModel().clearSelection();
                }
            }

            // Update Trace Log if Trace mode or Split mode is active
            if (btnModeTrace.isSelected() || btnModeSplit.isSelected()) {
                updateTraceLog();
            }
        });
    }

    private Task findTask(int pid) {
        if (kernel == null) return null;
        for (Task t : kernel.getAllTasks()) {
            if (t.getId() == pid) return t;
        }
        return null;
    }

    private RV32Cpu findCpuRunningTask(Task task) {
        if (kernel == null || task == null) return null;
        for (int i = 0; i < kernel.getConfig().getCoreCount(); i++) {
            RV32Cpu c = kernel.getCpu(i);
            if (task.equals(c.getCurrentTask())) {
                return c;
            }
        }
        return null;
    }

    private void updateTraceLog() {
        List<ExecutionTraceBuffer.TraceEvent> events = new ArrayList<>();
        int filterPid = (activeScope != null && activeScope.type == ScopeType.TASK) ? activeScope.id : -1;
        int filterHart = (activeScope != null && activeScope.type == ScopeType.HART) ? activeScope.id : -1;

        if (activeScope != null && activeScope.type == ScopeType.ALL_HARTS && kernel != null) {
            for (int i = 0; i < kernel.getConfig().getCoreCount(); i++) {
                events.addAll(kernel.getCpu(i).getTraceBuffer().getSnapshot(50, -1, -1));
            }
            events.sort(Comparator.comparingLong(e -> e.cycle));
            if (events.size() > 50) {
                events = events.subList(events.size() - 50, events.size());
            }
        } else if (activeScope != null && activeScope.type == ScopeType.TASK && kernel != null) {
            for (int i = 0; i < kernel.getConfig().getCoreCount(); i++) {
                events.addAll(kernel.getCpu(i).getTraceBuffer().getSnapshot(50, filterPid, -1));
            }
            events.sort(Comparator.comparingLong(e -> e.cycle));
            if (events.size() > 50) {
                events = events.subList(events.size() - 50, events.size());
            }
        } else {
            RV32Cpu targetCpu = (kernel != null && activeScope != null) ? kernel.getCpu(activeScope.id) : cpu;
            if (targetCpu != null) {
                events = targetCpu.getTraceBuffer().getSnapshot(50, filterPid, filterHart);
            }
        }

        boolean userOnly = (chkUserOnly != null && chkUserOnly.isSelected());
        Task activeTask = (activeScope != null && activeScope.type == ScopeType.TASK) ? findTask(activeScope.id) : null;
        int textStart = (activeTask != null && activeTask.getProgramInfo() != null) ? activeTask.getProgramInfo().textStart : 0x80000000;
        int textSize = (activeTask != null && activeTask.getProgramInfo() != null) ? activeTask.getProgramInfo().textSize : 0x100000;

        List<String> formatted = new ArrayList<>();
        for (ExecutionTraceBuffer.TraceEvent ev : events) {
            if (userOnly && (ev.pc < textStart || ev.pc >= textStart + textSize)) {
                continue;
            }
            String asm = Disassembler.disassemble(ev.instruction, ev.pc);
            String effect = "";
            if (ev.regWrite && ev.rd != 0) {
                effect = String.format("x%d <= 0x%08X", ev.rd, ev.aluResult);
            } else if (ev.memWrite) {
                effect = String.format("mem[0x%08X] <= 0x%08X", ev.aluResult, ev.aluResult);
            } else if (ev.aluResult != 0) {
                effect = String.format("res: 0x%08X", ev.aluResult);
            }
            formatted.add(String.format("C%-7d | Hart %d | P%-2d | %08x: %-24s | %s",
                    ev.cycle, ev.hartId, ev.pid, ev.pc, asm, effect));
        }

        traceItems.setAll(formatted);

        if (chkFollowPc != null && chkFollowPc.isSelected() && !traceItems.isEmpty()) {
            traceListView.scrollTo(traceItems.size() - 1);
        }
    }

    private void rebuildFullList(Task task) {
        instructions.clear();
        addressToIndexMap.clear();

        ProgramInfo pinfo = task.getProgramInfo();
        if (pinfo == null) {
            instructions.add(new InstructionItem(0, 0, "No Program Info Available", false));
            return;
        }

        int start = pinfo.textStart;
        int size = pinfo.textSize;
        if (size > 1024 * 1024) size = 1024 * 1024; // 1MB cap

        int end = start + size;
        Map<Integer, String> symbolMap = pinfo.symbols;
        int pid = task.getId();
        int listIndex = 0;

        int addr = start;
        while (addr < end) {
            if (symbolMap != null && symbolMap.containsKey(addr)) {
                String symName = symbolMap.get(addr);
                String labelStr = String.format("%08x <%s>:", addr, symName);
                instructions.add(new InstructionItem(addr, 0, labelStr, true));
                listIndex++;
            }

            try {
                int lower16 = memory.debugReadWord(addr, pid) & 0xFFFF;
                String asm;
                int code;
                int step;

                if (lower16 == 0) {
                    code = memory.debugReadWord(addr, pid);
                    asm = ".word 0";
                    step = (code == 0) ? 4 : 4;
                    if ((lower16 & 0x3) != 0x3) {
                        step = 2;
                        code = lower16;
                    }
                } else if ((lower16 & 0x3) != 0x3) {
                    code = lower16;
                    asm = Disassembler.disassembleCompressed(lower16, addr);
                    step = 2;
                } else {
                    code = memory.debugReadWord(addr, pid);
                    asm = Disassembler.disassemble(code, addr);
                    step = 4;
                }

                asm = asm.replace(",", "");
                if (asm.contains("ecall")) {
                    asm += "  <-- Syscall / Trap";
                }

                instructions.add(new InstructionItem(addr, code, asm, false));
                addressToIndexMap.put(addr, listIndex);
                listIndex++;
                addr += step;
            } catch (Exception e) {
                instructions.add(new InstructionItem(addr, 0, "???", false));
                listIndex++;
                addr += 2;
            }
        }
    }

    private class InstructionCell extends ListCell<InstructionItem> {
        public InstructionCell() {
            setOnMouseClicked(e -> {
                if (getItem() != null && !isEmpty()) {
                    toggleItemBreakpoint(getItem().address);
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

                boolean hasBp = checkItemBreakpoint(item.address);
                if (hasBp) {
                    setGraphic(new javafx.scene.shape.Circle(5, javafx.scene.paint.Color.RED));
                } else {
                    setGraphic(null);
                }

                if (item.isLabel) {
                    setText(item.assembly);
                    setStyle("-fx-font-weight: bold; -fx-text-fill: black; -fx-background-color: transparent;");
                } else {
                    String hexCode = (item.code <= 0xFFFF && item.code >= 0)
                            ? String.format("%04x", item.code)
                            : String.format("%08x", item.code);

                    long hitCount = getCpuHitCount(item.address);
                    if (hitCount > 0) {
                        setText(String.format("    %08x:    %s    %-32s [ x %,d ]", item.address, hexCode, item.assembly, hitCount));
                    } else {
                        setText(String.format("    %08x:    %s    %s", item.address, hexCode, item.assembly));
                    }

                    boolean isAtomic = item.assembly.trim().startsWith("amo") ||
                            item.assembly.trim().startsWith("lr.") ||
                            item.assembly.trim().startsWith("sc.");

                    if (item.address == currentPc) {
                        if (isAtomic) {
                            setStyle("-fx-background-color: #9c27b0; -fx-text-fill: white; -fx-font-weight: bold;");
                        } else {
                            setStyle("-fx-background-color: #d4e157; -fx-text-fill: black;");
                        }
                    } else if (item.address == animationPreviousPc) {
                        if (isAtomic) {
                            setStyle("-fx-background-color: #ba68c8; -fx-text-fill: white; -fx-font-weight: bold;");
                        } else {
                            setStyle("-fx-background-color: #f0f4c3; -fx-text-fill: black;");
                        }
                    } else if (hitCount > 1000) {
                        setStyle("-fx-background-color: #ffe0b2; -fx-text-fill: #e65100; -fx-font-weight: bold;");
                    } else if (hitCount > 0) {
                        setStyle("-fx-background-color: #fff3e0; -fx-text-fill: black;");
                    } else {
                        if (isAtomic) {
                            setStyle("-fx-background-color: #e1bee7; -fx-text-fill: #4a148c; -fx-font-weight: bold;");
                        } else {
                            setStyle("-fx-background-color: transparent; -fx-text-fill: black;");
                        }
                    }
                }
            }
        }
    }

    private void toggleItemBreakpoint(int address) {
        if (activeScope != null && activeScope.type == ScopeType.HART && cpu != null) {
            cpu.toggleBreakpoint(address);
        } else if (kernel != null) {
            for (int i = 0; i < kernel.getConfig().getCoreCount(); i++) {
                kernel.getCpu(i).toggleBreakpoint(address);
            }
        } else if (cpu != null) {
            cpu.toggleBreakpoint(address);
        }
    }

    private boolean checkItemBreakpoint(int address) {
        if (activeScope != null && activeScope.type == ScopeType.HART && cpu != null) {
            return cpu.hasBreakpoint(address);
        }
        if (kernel != null) {
            for (int i = 0; i < kernel.getConfig().getCoreCount(); i++) {
                if (kernel.getCpu(i).hasBreakpoint(address)) {
                    return true;
                }
            }
        }
        return cpu != null && cpu.hasBreakpoint(address);
    }

    private long getCpuHitCount(int address) {
        if (cpu != null) {
            return cpu.getHitCount(address);
        }
        return 0L;
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
