package cse311.gui.components;

import cse311.Disassembler;
import cse311.RV32Cpu;
import cse311.pipeline.PipelineRegisters;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * 5-Stage RISC-V Pipeline Visualizer Component.
 * Displays IF, ID, EX, MEM, and WB stages with instruction flow,
 * hazard status, data forwarding state, and real-time IPC metrics.
 * 
 * Styled cleanly to match the application theme defined in style.css.
 */
public class PipelineView extends VBox {

    private static final String PC_EMPTY = "PC: ---";
    private static final String PC_FMT = "PC: 0x%08X";
    private static final String STAGE_BUBBLE = "Bubble";
    private static final String CSS_INST_LABEL = "pipeline-inst-label";
    private static final String CSS_INST_EMPTY = "pipeline-inst-empty";
    private static final String CSS_BADGE_SINGLE = "pipeline-badge-mode-single";
    private static final String CSS_BADGE_PIPELINED = "pipeline-badge-mode-pipelined";
    private static final String MODE_SINGLE_CYCLE = "SINGLE-CYCLE";
    private static final String MODE_PIPELINE = "5-STAGE PIPELINE";
    private static final String BTN_TEXT_PIPELINE = "Switch to 5-Stage Pipeline";
    private static final String BTN_TEXT_SINGLE = "Switch to Single-Cycle";

    private RV32Cpu cpu;

    // Header Controls & Mode
    private Label lblModeStatus;
    private CheckBox chkForwarding;
    private CheckBox chkCacheSim;
    private Button btnSwitchMode;
    private Button btnStepCycle;

    // Metric Value Labels
    private Label lblCyclesVal;
    private Label lblCommittedVal;
    private Label lblStallsVal;
    private Label lblFlushesVal;
    private Label lblIpcVal;
    private Label lblICacheVal;
    private Label lblDCacheVal;
    private VBox chipICache;
    private VBox chipDCache;

    // Stage Panels
    private static class StagePanel {
        VBox root;
        Label lblInst;
        Label lblPc;
        Label lblDetails;
    }

    private StagePanel stageIf;
    private StagePanel stageId;
    private StagePanel stageEx;
    private StagePanel stageMem;
    private StagePanel stageWb;

    // Inactive Overlay
    private StackPane contentStack;
    private VBox inactiveOverlay;

    // Trace Log
    private ListView<String> lstTraceLog;
    private ObservableList<String> traceLogItems;

    private Runnable onModeToggle;
    private long lastCycleCount = -1;
    private RV32Cpu.ExecutionMode lastMode;
    private boolean forceUpdate = true;

    public PipelineView(RV32Cpu cpu) {
        super();
        this.cpu = cpu;
        this.setSpacing(10);
        this.setPadding(new Insets(12));
        this.getStyleClass().add("pipeline-root");

        // Ensure stylesheet is loaded if component is instantiated standalone
        String css = getClass().getResource("/css/style.css") != null
                ? getClass().getResource("/css/style.css").toExternalForm() : null;
        if (css != null && !this.getStylesheets().contains(css)) {
            this.getStylesheets().add(css);
        }

        buildHeaderToolbar();

        contentStack = new StackPane();
        HBox stagesBox = buildStagesRow();
        inactiveOverlay = buildInactiveOverlay();

        contentStack.getChildren().addAll(stagesBox, inactiveOverlay);
        VBox.setVgrow(contentStack, Priority.ALWAYS);

        VBox traceLogBox = buildTraceLogPanel();

        this.getChildren().addAll(contentStack, traceLogBox);
        update();
    }

    public void setCpu(RV32Cpu cpu) {
        this.cpu = cpu;
        this.forceUpdate = true;
        update();
    }

    public void setOnModeToggle(Runnable listener) {
        this.onModeToggle = listener;
    }

    private static String formatPc(int pc) {
        return String.format(PC_FMT, pc);
    }

    private void buildHeaderToolbar() {
        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8, 12, 8, 12));
        toolbar.getStyleClass().add("pipeline-toolbar");

        lblModeStatus = new Label(MODE_SINGLE_CYCLE);
        lblModeStatus.getStyleClass().add(CSS_BADGE_SINGLE);
        lblModeStatus.setPadding(new Insets(4, 8, 4, 8));

        btnSwitchMode = new Button(BTN_TEXT_PIPELINE);
        btnSwitchMode.getStyleClass().add("pipeline-btn-primary");
        btnSwitchMode.setOnAction(e -> toggleExecutionMode());

        btnStepCycle = new Button("Step Cycle");
        btnStepCycle.getStyleClass().add("pipeline-btn-secondary");
        btnStepCycle.setOnAction(e -> {
            if (cpu != null) {
                cpu.clockTick();
                update();
            }
        });

        chkForwarding = new CheckBox("Forwarding");
        chkForwarding.setSelected(true);
        chkForwarding.getStyleClass().add("pipeline-checkbox");
        chkForwarding.setOnAction(e -> {
            if (cpu != null) {
                cpu.setForwardingEnabled(chkForwarding.isSelected());
                update();
            }
        });

        chkCacheSim = new CheckBox("L1 Cache");
        chkCacheSim.setSelected(false);
        chkCacheSim.getStyleClass().add("pipeline-checkbox");
        chkCacheSim.setOnAction(e -> {
            if (cpu != null) {
                cpu.setCacheSimulationEnabled(chkCacheSim.isSelected());
                update();
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Metrics Flow
        HBox metricsBox = new HBox(6);
        metricsBox.setAlignment(Pos.CENTER_RIGHT);

        lblCyclesVal = new Label("0");
        VBox chipCycles = createMetricChip("CYCLES", lblCyclesVal);

        lblCommittedVal = new Label("0");
        VBox chipCommitted = createMetricChip("COMMITTED", lblCommittedVal);

        lblStallsVal = new Label("0");
        VBox chipStalls = createMetricChip("STALLS", lblStallsVal);

        lblFlushesVal = new Label("0");
        VBox chipFlushes = createMetricChip("FLUSHES", lblFlushesVal);

        lblIpcVal = new Label("0.00");
        VBox chipIpc = createMetricChip("IPC", lblIpcVal);

        lblICacheVal = new Label("100.0%");
        chipICache = createMetricChip("I-CACHE", lblICacheVal);

        lblDCacheVal = new Label("100.0%");
        chipDCache = createMetricChip("D-CACHE", lblDCacheVal);

        metricsBox.getChildren().addAll(chipCycles, chipCommitted, chipStalls, chipFlushes, chipIpc, chipICache, chipDCache);

        toolbar.getChildren().addAll(lblModeStatus, btnSwitchMode, btnStepCycle, chkForwarding, chkCacheSim, spacer, metricsBox);
        this.getChildren().add(toolbar);
    }

    private VBox createMetricChip(String title, Label valLabel) {
        VBox chip = new VBox(1);
        chip.setAlignment(Pos.CENTER);
        chip.getStyleClass().add("pipeline-metric-chip");

        Label lblTitle = new Label(title);
        lblTitle.getStyleClass().add("pipeline-metric-title");

        valLabel.getStyleClass().add("pipeline-metric-value");

        chip.getChildren().addAll(lblTitle, valLabel);
        return chip;
    }

    private HBox buildStagesRow() {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER);
        HBox.setHgrow(row, Priority.ALWAYS);

        stageIf = createStagePanel("1. IF (Fetch)", "if");
        stageId = createStagePanel("2. ID (Decode)", "id");
        stageEx = createStagePanel("3. EX (Execute)", "ex");
        stageMem = createStagePanel("4. MEM (Memory)", "mem");
        stageWb = createStagePanel("5. WB (Write-Back)", "wb");

        row.getChildren().addAll(stageIf.root, stageId.root, stageEx.root, stageMem.root, stageWb.root);
        return row;
    }

    private StagePanel createStagePanel(String title, String stageKey) {
        StagePanel p = new StagePanel();

        VBox card = new VBox(0);
        card.getStyleClass().add("pipeline-stage-card");
        HBox.setHgrow(card, Priority.ALWAYS);

        // Header
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(6, 10, 6, 10));
        header.getStyleClass().add("pipeline-stage-header-" + stageKey);

        Label lblTitle = new Label(title);
        lblTitle.getStyleClass().add("pipeline-stage-title-" + stageKey);
        header.getChildren().add(lblTitle);

        // Content
        VBox content = new VBox(8);
        content.setPadding(new Insets(10));
        content.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(content, Priority.ALWAYS);

        // Instruction Box
        VBox instBox = new VBox(2);
        instBox.getStyleClass().add("pipeline-inst-box");
        instBox.setAlignment(Pos.CENTER_LEFT);

        p.lblPc = new Label(PC_EMPTY);
        p.lblPc.getStyleClass().add("pipeline-pc-label");

        p.lblInst = new Label("Bubble / Stall");
        p.lblInst.getStyleClass().add(CSS_INST_EMPTY);

        instBox.getChildren().addAll(p.lblPc, p.lblInst);

        // Details
        p.lblDetails = new Label("");
        p.lblDetails.getStyleClass().add("pipeline-details-box");
        p.lblDetails.setMinHeight(48);

        content.getChildren().addAll(instBox, p.lblDetails);
        card.getChildren().addAll(header, content);

        p.root = card;
        return p;
    }

    private VBox buildInactiveOverlay() {
        VBox overlay = new VBox();
        overlay.setAlignment(Pos.CENTER);
        overlay.setPadding(new Insets(24));
        overlay.getStyleClass().add("pipeline-overlay-backdrop");

        VBox card = new VBox(12);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(500);
        card.getStyleClass().add("pipeline-overlay-card");

        Label lblTitle = new Label("Pipeline Simulation Inactive");
        lblTitle.getStyleClass().add("pipeline-overlay-title");

        Label lblMsg = new Label("The CPU is currently running in Single-Cycle Execution Mode.\nSwitch to 5-stage pipeline mode to inspect cycle-by-cycle stage registers, hazard detection, and data forwarding.");
        lblMsg.getStyleClass().add("pipeline-overlay-msg");

        Button btnActivate = new Button("Activate 5-Stage Pipeline Mode");
        btnActivate.getStyleClass().add("pipeline-btn-primary");
        btnActivate.setOnAction(e -> toggleExecutionMode());

        card.getChildren().addAll(lblTitle, lblMsg, btnActivate);
        overlay.getChildren().add(card);
        return overlay;
    }

    private void toggleExecutionMode() {
        if (onModeToggle != null) {
            onModeToggle.run();
            return;
        }
        if (cpu == null) return;
        if (cpu.getExecutionMode() == RV32Cpu.ExecutionMode.SINGLE_CYCLE) {
            cpu.setExecutionMode(RV32Cpu.ExecutionMode.FIVE_STAGE_PIPELINE);
        } else {
            cpu.setExecutionMode(RV32Cpu.ExecutionMode.SINGLE_CYCLE);
        }
        update();
    }

    public final void update() {
        if (cpu == null) return;

        RV32Cpu.ExecutionMode currentMode = cpu.getExecutionMode();
        long cycles = cpu.getPipelineClockCycles();

        if (!forceUpdate && currentMode == lastMode && cycles == lastCycleCount) {
            return;
        }
        forceUpdate = false;
        lastMode = currentMode;
        lastCycleCount = cycles;

        boolean isPipelined = (currentMode == RV32Cpu.ExecutionMode.FIVE_STAGE_PIPELINE);
        inactiveOverlay.setVisible(!isPipelined);
        chkForwarding.setDisable(!isPipelined);
        btnStepCycle.setDisable(!isPipelined);

        if (isPipelined) {
            lblModeStatus.setText(MODE_PIPELINE);
            lblModeStatus.getStyleClass().setAll(CSS_BADGE_PIPELINED);
            btnSwitchMode.setText(BTN_TEXT_SINGLE);
        } else {
            lblModeStatus.setText(MODE_SINGLE_CYCLE);
            lblModeStatus.getStyleClass().setAll(CSS_BADGE_SINGLE);
            btnSwitchMode.setText(BTN_TEXT_PIPELINE);
        }

        // Metrics
        lblCyclesVal.setText(String.valueOf(cpu.getPipelineClockCycles()));
        lblCommittedVal.setText(String.valueOf(cpu.getPipelineCommittedInstructions()));
        lblStallsVal.setText(String.valueOf(cpu.getPipelineStalls()));
        lblFlushesVal.setText(String.valueOf(cpu.getPipelineFlushes()));
        lblIpcVal.setText(String.format("%.2f", cpu.getIpc()));

        chkForwarding.setSelected(cpu.isForwardingEnabled());
        chkCacheSim.setSelected(cpu.isCacheSimulationEnabled());
        chkCacheSim.setDisable(!isPipelined);

        if (cpu.isCacheSimulationEnabled()) {
            lblICacheVal.setText(String.format("%.1f%%", cpu.getICache().getHitRate()));
            lblDCacheVal.setText(String.format("%.1f%%", cpu.getDCache().getHitRate()));
            chipICache.setVisible(true);
            chipICache.setManaged(true);
            chipDCache.setVisible(true);
            chipDCache.setManaged(true);
        } else {
            chipICache.setVisible(false);
            chipICache.setManaged(false);
            chipDCache.setVisible(false);
            chipDCache.setManaged(false);
        }

        // Update Stages
        updateStageIF(cpu.ifId);
        updateStageID(cpu.idEx);
        updateStageEX(cpu.exMem);
        updateStageMEM(cpu.memWb);
        updateStageWB(cpu.memWb);

        updateTraceLog();
    }

    private VBox buildTraceLogPanel() {
        VBox pnl = new VBox(6);
        pnl.setPadding(new Insets(10));
        pnl.getStyleClass().add("pipeline-trace-panel");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Committed Instruction Trace");
        title.getStyleClass().add("pipeline-trace-title");

        Label subtitle = new Label("Retired instruction execution history");
        subtitle.getStyleClass().add("pipeline-trace-subtitle");

        header.getChildren().addAll(title, subtitle);

        traceLogItems = FXCollections.observableArrayList();
        lstTraceLog = new ListView<>(traceLogItems);
        lstTraceLog.setPrefHeight(120);
        lstTraceLog.getStyleClass().add("pipeline-trace-list");

        pnl.getChildren().addAll(header, lstTraceLog);
        return pnl;
    }

    private void updateTraceLog() {
        if (cpu == null || traceLogItems == null) return;
        List<cse311.pipeline.ExecutionTraceBuffer.TraceEvent> events = cpu.getTraceBuffer().getSnapshot(15);
        traceLogItems.clear();
        for (cse311.pipeline.ExecutionTraceBuffer.TraceEvent ev : events) {
            String asm = disassemble(ev.instruction, ev.pc);
            String itemStr = String.format("Cycle %-6d  |  PC: 0x%08X  |  %-28s  |  Result: 0x%08X",
                    ev.cycle, ev.pc, asm, ev.aluResult);
            traceLogItems.add(itemStr);
        }
    }

    private void setInstructionText(StagePanel stage, String asm, boolean isActive) {
        stage.lblInst.setText(asm);
        stage.lblInst.getStyleClass().setAll(isActive ? CSS_INST_LABEL : CSS_INST_EMPTY);
    }

    private void updateStageIF(PipelineRegisters.IF_ID ifId) {
        if (cpu != null && cpu.isICacheStalling()) {
            setInstructionText(stageIf, "I-Cache Miss (RAM Stall)", false);
            stageIf.lblPc.setText(formatPc(cpu.getProgramCounter()));
            stageIf.lblDetails.setText("Waiting for memory subsystem...");
        } else if (ifId != null && ifId.valid) {
            setInstructionText(stageIf, disassemble(ifId.instruction, ifId.pc), true);
            stageIf.lblPc.setText(formatPc(ifId.pc));
            stageIf.lblDetails.setText(String.format("Raw: 0x%08X", ifId.instruction));
        } else {
            setInstructionText(stageIf, "Stall / NOP", false);
            stageIf.lblPc.setText(PC_EMPTY);
            stageIf.lblDetails.setText("");
        }
    }

    private void updateStageID(PipelineRegisters.ID_EX idEx) {
        if (idEx != null && idEx.valid) {
            setInstructionText(stageId, disassemble(idEx.instruction, idEx.pc), true);
            stageId.lblPc.setText(formatPc(idEx.pc));
            stageId.lblDetails.setText(String.format("rs1=x%d (0x%X)\nrs2=x%d (0x%X)\nrd=x%d, imm=%d",
                    idEx.rs1, idEx.val1, idEx.rs2, idEx.val2, idEx.rd, idEx.imm));
        } else {
            setInstructionText(stageId, STAGE_BUBBLE, false);
            stageId.lblPc.setText(PC_EMPTY);
            stageId.lblDetails.setText("");
        }
    }

    private void updateStageEX(PipelineRegisters.EX_MEM exMem) {
        if (exMem != null && exMem.valid) {
            setInstructionText(stageEx, disassemble(exMem.instruction, exMem.pc), true);
            stageEx.lblPc.setText(formatPc(exMem.pc));
            stageEx.lblDetails.setText(String.format("ALU Result: 0x%X (%d)\nrd: x%d | regWrite=%b",
                    exMem.aluResult, exMem.aluResult, exMem.rd, exMem.regWrite));
        } else {
            setInstructionText(stageEx, STAGE_BUBBLE, false);
            stageEx.lblPc.setText(PC_EMPTY);
            stageEx.lblDetails.setText("");
        }
    }

    private void updateStageMEM(PipelineRegisters.MEM_WB memWb) {
        if (cpu != null && cpu.isDCacheStalling()) {
            setInstructionText(stageMem, "D-Cache Miss (RAM Stall)", false);
            stageMem.lblPc.setText(formatPc(cpu.exMem.pc));
            stageMem.lblDetails.setText(String.format("Address: 0x%X\nWaiting for RAM...", cpu.exMem.aluResult));
        } else if (memWb != null && memWb.valid) {
            setInstructionText(stageMem, disassemble(memWb.instruction, memWb.pc), true);
            stageMem.lblPc.setText(formatPc(memWb.pc));
            stageMem.lblDetails.setText(String.format("Mem Data: 0x%X\nALU Out: 0x%X\nrd: x%d",
                    memWb.memReadData, memWb.aluResult, memWb.rd));
        } else {
            setInstructionText(stageMem, STAGE_BUBBLE, false);
            stageMem.lblPc.setText(PC_EMPTY);
            stageMem.lblDetails.setText("");
        }
    }

    private void updateStageWB(PipelineRegisters.MEM_WB memWb) {
        if (memWb != null && memWb.valid && memWb.regWrite && memWb.rd != 0) {
            int val = memWb.memToReg ? memWb.memReadData : memWb.aluResult;
            setInstructionText(stageWb, disassemble(memWb.instruction, memWb.pc), true);
            stageWb.lblPc.setText(formatPc(memWb.pc));
            stageWb.lblDetails.setText(String.format("Write: x%d <= 0x%X", memWb.rd, val));
        } else {
            setInstructionText(stageWb, "NOP / Inactive", false);
            stageWb.lblPc.setText(PC_EMPTY);
            stageWb.lblDetails.setText("");
        }
    }

    private String disassemble(int instruction, int pc) {
        if (instruction == 0 || instruction == 0x00000013) return "NOP";
        return Disassembler.disassemble(instruction, pc);
    }
}
