package cse311.gui.components;

import cse311.InstructionDecoded;
import cse311.RV32Cpu;
import javafx.animation.PathTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.Map;

/**
 * Responsive DatapathView - uses percentage-based positioning
 * that automatically scales with container size.
 */
public class DatapathView extends AnchorPane {

    private RV32Cpu cpu;

    // Reference sizes for percentage calculations
    private static final double REF_WIDTH = 1920.0;
    private static final double REF_HEIGHT = 1080.0;

    // Component positions as percentages (x%, y%, width%, height%)
    private static final double[] PC_POS = { 8, 42, 4, 9 };
    private static final double[] PC_ADD_POS = { 7, 28, 6, 6 };
    private static final double[] IM_POS = { 17, 35, 11, 22 };
    private static final double[] CONTROL_POS = { 30, 10, 12, 10 };
    private static final double[] REG_POS = { 38, 35, 11, 22 };
    private static final double[] IMM_POS = { 38, 65, 9, 8 };
    private static final double[] ALU_POS = { 55, 38, 9, 13 };
    private static final double[] DM_POS = { 70, 35, 11, 22 };
    private static final double[] MUX_WB_POS = { 86, 42, 4, 9 };
    private static final double[] LEGEND_POS = { 83, 3, 15, 38 };

    // Component shapes
    private StackPane pcPane, imPane, regPane, immPane, aluPane, dmPane, muxWbPane, controlPane, pcAddPane;
    private Rectangle compPc, compIm, compReg, compImm, compAlu, compDm, compMuxWb, compControl, compPcAdd;

    // Wires and arrows
    private Polyline wirePcIm, wireImReg, wireImImm, wireRegAluA, wireRegAluB, wireImmAluB;
    private Polyline wireAluDmAddr, wireRegDmData, wireDmMux, wireAluMux, wireWbReg;
    private Polyline wireImControl, wirePcAdd;

    private Polygon arrowPcIm, arrowImReg, arrowImImm, arrowRegAluA, arrowRegAluB, arrowImmAluB;
    private Polygon arrowAluDmAddr, arrowRegDmData, arrowDmMux, arrowAluMux, arrowWbReg;
    private Polygon arrowImControl, arrowPcAdd;

    // Labels
    private Text txtPC, txtInstruction, txtControlSignals;
    private Text txtRs1Val, txtRs2Val, txtAluResult, txtAluOp;
    private Label lblRegWrite, lblMemRead, lblMemWrite, lblALUSrc;
    private Label lblPcTitle, lblImTitle, lblRegTitle, lblImmTitle, lblAluTitle, lblDmTitle, lblMuxTitle;
    private VBox legendBox;

    // Maps for highlighting
    private final Map<String, Shape> components = new HashMap<>();
    private final Map<String, Shape> wires = new HashMap<>();
    private final Map<String, Polygon> arrows = new HashMap<>();

    public DatapathView(RV32Cpu cpu) {
        this.cpu = cpu;
        // this.getStyleClass().add("datapath-root"); // Removed to fix zooming issue
        // with background scaling
        this.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

        setPrefSize(REF_WIDTH, REF_HEIGHT);
        setMinSize(1280.0, 720.0);

        buildComponents();
        buildWires();
        buildLabels();
        buildLegend();
        initializeMaps();
        initializeTooltips();
        setupZoomAndPan();

        // Responsive resize listener
        widthProperty().addListener((obs, oldVal, newVal) -> layoutComponents());
        heightProperty().addListener((obs, oldVal, newVal) -> layoutComponents());

        // Initial layout - use Platform.runLater to ensure proper sizing
        javafx.application.Platform.runLater(this::layoutComponents);
    }

    public void setCpu(RV32Cpu cpu) {
        this.cpu = cpu;
        update();
    }

    // ===================== COMPONENT BUILDERS =====================

    private void buildComponents() {
        // PC
        pcPane = createComponent("PC", "datapath-pc", PC_POS[2], PC_POS[3]);
        compPc = (Rectangle) ((StackPane) pcPane).getChildren().get(0);

        // PC+4 Adder
        pcAddPane = createComponent("PC + 4", "datapath-adder", PC_ADD_POS[2], PC_ADD_POS[3]);
        compPcAdd = (Rectangle) ((StackPane) pcAddPane).getChildren().get(0);

        // Instruction Memory
        imPane = createComponent("Instruction\nMemory", "datapath-memory", IM_POS[2], IM_POS[3]);
        compIm = (Rectangle) ((StackPane) imPane).getChildren().get(0);

        // Control Unit
        controlPane = createComponent("Control\nUnit", "datapath-control", CONTROL_POS[2], CONTROL_POS[3]);
        compControl = (Rectangle) ((StackPane) controlPane).getChildren().get(0);

        // Register File
        regPane = createComponentWithSublabel("Register\nFile", "(x0-x31)", "datapath-reg", REG_POS[2], REG_POS[3]);
        compReg = (Rectangle) ((StackPane) regPane).getChildren().get(0);

        // Immediate Generator
        immPane = createComponent("Imm Gen", "datapath-imm", IMM_POS[2], IMM_POS[3]);
        compImm = (Rectangle) ((StackPane) immPane).getChildren().get(0);

        // ALU
        aluPane = createComponent("ALU", "datapath-alu", ALU_POS[2], ALU_POS[3]);
        compAlu = (Rectangle) ((StackPane) aluPane).getChildren().get(0);

        // Data Memory
        dmPane = createComponent("Data\nMemory", "datapath-memory", DM_POS[2], DM_POS[3]);
        compDm = (Rectangle) ((StackPane) dmPane).getChildren().get(0);

        // Write Back Mux
        muxWbPane = createComponent("M", "datapath-mux", MUX_WB_POS[2], MUX_WB_POS[3]);
        compMuxWb = (Rectangle) ((StackPane) muxWbPane).getChildren().get(0);

        // Add all to parent
        getChildren().addAll(pcPane, pcAddPane, imPane, controlPane, regPane, immPane, aluPane, dmPane, muxWbPane);
    }

    private StackPane createComponent(String label, String typeClass, double widthPct, double heightPct) {
        Rectangle rect = new Rectangle();
        rect.getStyleClass().addAll("datapath-component", typeClass);
        rect.setArcWidth(8);
        rect.setArcHeight(8);

        Label lbl = new Label(label);
        lbl.getStyleClass().add("component-label-inside");
        lbl.setWrapText(true);
        lbl.setAlignment(Pos.CENTER);
        lbl.setStyle("-fx-text-alignment: center;");

        StackPane pane = new StackPane(rect, lbl);
        pane.setAlignment(Pos.CENTER);
        return pane;
    }

    private StackPane createComponentWithSublabel(String label, String sublabel, String typeClass, double widthPct,
            double heightPct) {
        Rectangle rect = new Rectangle();
        rect.getStyleClass().addAll("datapath-component", typeClass);
        rect.setArcWidth(8);
        rect.setArcHeight(8);

        Label mainLbl = new Label(label);
        mainLbl.getStyleClass().add("component-label-inside");
        mainLbl.setWrapText(true);
        mainLbl.setAlignment(Pos.CENTER);
        mainLbl.setStyle("-fx-text-alignment: center;");

        Label subLbl = new Label(sublabel);
        subLbl.getStyleClass().add("component-sublabel");

        VBox vbox = new VBox(2, mainLbl, subLbl);
        vbox.setAlignment(Pos.CENTER);

        StackPane pane = new StackPane(rect, vbox);
        pane.setAlignment(Pos.CENTER);
        return pane;
    }

    // ===================== WIRE BUILDERS =====================

    private void buildWires() {
        // Data wires
        wirePcIm = createWire("wire-inactive");
        wireImReg = createWire("wire-inactive");
        wireImImm = createWire("wire-inactive");
        wireRegAluA = createWire("wire-inactive");
        wireRegAluB = createWire("wire-inactive");
        wireImmAluB = createWire("wire-inactive");
        wireAluDmAddr = createWire("wire-inactive");
        wireRegDmData = createWire("wire-inactive");
        wireDmMux = createWire("wire-inactive");
        wireAluMux = createWire("wire-inactive");
        wireWbReg = createWire("wire-inactive");

        // Control wire
        wireImControl = createWire("wire-control");
        wirePcAdd = createWire("wire-inactive");

        // Arrows
        arrowPcIm = createArrow();
        arrowImReg = createArrow();
        arrowImImm = createArrow();
        arrowRegAluA = createArrow();
        arrowRegAluB = createArrow();
        arrowImmAluB = createArrow();
        arrowAluDmAddr = createArrow();
        arrowRegDmData = createArrow();
        arrowDmMux = createArrow();
        arrowAluMux = createArrow();
        arrowWbReg = createArrow();
        arrowImControl = createArrow();
        arrowPcAdd = createArrow();

        // Add wires first (so they're behind components)
        getChildren().addAll(0, java.util.List.of(
                wirePcIm, wireImReg, wireImImm, wireRegAluA, wireRegAluB, wireImmAluB,
                wireAluDmAddr, wireRegDmData, wireDmMux, wireAluMux, wireWbReg,
                wireImControl, wirePcAdd,
                arrowPcIm, arrowImReg, arrowImImm, arrowRegAluA, arrowRegAluB, arrowImmAluB,
                arrowAluDmAddr, arrowRegDmData, arrowDmMux, arrowAluMux, arrowWbReg,
                arrowImControl, arrowPcAdd));
    }

    private Polyline createWire(String styleClass) {
        Polyline wire = new Polyline();
        wire.getStyleClass().add(styleClass);
        return wire;
    }

    private Polygon createArrow() {
        Polygon arrow = new Polygon();
        arrow.getStyleClass().add("arrow-head");
        return arrow;
    }

    // ===================== LABEL BUILDERS =====================

    private void buildLabels() {
        txtPC = new Text("PC: --");
        txtPC.getStyleClass().add("value-label");

        txtInstruction = new Text("Type: --");
        txtInstruction.getStyleClass().add("value-label");

        txtControlSignals = new Text("Control: ");
        txtControlSignals.getStyleClass().add("control-label");

        txtRs1Val = new Text("0x00000000");
        txtRs1Val.getStyleClass().add("value-label");
        txtRs1Val.setVisible(false);

        txtRs2Val = new Text("0x00000000");
        txtRs2Val.getStyleClass().add("value-label");
        txtRs2Val.setVisible(false);

        txtAluResult = new Text("0x00000000");
        txtAluResult.getStyleClass().add("value-label");
        txtAluResult.setVisible(false);

        txtAluOp = new Text("");
        txtAluOp.getStyleClass().add("value-label-small");

        // Control signal labels
        lblRegWrite = new Label("RegWrite");
        lblRegWrite.getStyleClass().add("control-signal-label");
        lblMemRead = new Label("MemRead");
        lblMemRead.getStyleClass().add("control-signal-label");
        lblMemWrite = new Label("MemWrite");
        lblMemWrite.getStyleClass().add("control-signal-label");
        lblALUSrc = new Label("ALUSrc");
        lblALUSrc.getStyleClass().add("control-signal-label");

        // Component title labels
        lblPcTitle = new Label("Program\nCounter");
        lblPcTitle.getStyleClass().add("component-label");
        lblImTitle = new Label("I-Mem");
        lblImTitle.getStyleClass().add("component-label");
        lblRegTitle = new Label("Registers");
        lblRegTitle.getStyleClass().add("component-label");
        lblImmTitle = new Label("Immediate\nGenerator");
        lblImmTitle.getStyleClass().add("component-label");
        lblAluTitle = new Label("Arithmetic\nLogic Unit");
        lblAluTitle.getStyleClass().add("component-label");
        lblDmTitle = new Label("D-Mem");
        lblDmTitle.getStyleClass().add("component-label");
        lblMuxTitle = new Label("WB Mux");
        lblMuxTitle.getStyleClass().add("component-label");

        getChildren().addAll(
                txtPC, txtInstruction, txtControlSignals, txtRs1Val, txtRs2Val, txtAluResult, txtAluOp,
                lblRegWrite, lblMemRead, lblMemWrite, lblALUSrc,
                lblPcTitle, lblImTitle, lblRegTitle, lblImmTitle, lblAluTitle, lblDmTitle, lblMuxTitle);
    }

    // ===================== LEGEND =====================

    private void buildLegend() {
        legendBox = new VBox(6);
        legendBox.getStyleClass().add("legend-box");
        legendBox.setPadding(new Insets(10, 12, 10, 12));

        Label title = new Label("Legend");
        title.getStyleClass().add("legend-title");
        legendBox.getChildren().add(title);

        addLegendItem("Program Counter", "datapath-pc");
        addLegendItem("Control Unit", "datapath-control");
        addLegendItem("Memory", "datapath-memory");
        addLegendItem("Registers", "datapath-reg");
        addLegendItem("ALU", "datapath-alu");
        addLegendItem("Immediate Gen", "datapath-imm");

        // Wire legends
        HBox inactiveWire = new HBox(8);
        inactiveWire.setAlignment(Pos.CENTER_LEFT);
        Polyline inactiveLine = new Polyline(0, 7, 14, 7);
        inactiveLine.getStyleClass().add("wire-inactive");
        inactiveLine.setStyle("-fx-stroke-width: 2.5;");
        Label inactiveLbl = new Label("Inactive Wire");
        inactiveLbl.getStyleClass().add("legend-label");
        inactiveWire.getChildren().addAll(inactiveLine, inactiveLbl);
        legendBox.getChildren().add(inactiveWire);

        HBox activeWire = new HBox(8);
        activeWire.setAlignment(Pos.CENTER_LEFT);
        Polyline activeLine = new Polyline(0, 7, 14, 7);
        activeLine.getStyleClass().add("datapath-active");
        activeLine.setStyle("-fx-stroke-width: 2.5;");
        Label activeLbl = new Label("Active Data Flow");
        activeLbl.getStyleClass().add("legend-label");
        activeWire.getChildren().addAll(activeLine, activeLbl);
        legendBox.getChildren().add(activeWire);

        getChildren().add(legendBox);
    }

    private void addLegendItem(String text, String colorClass) {
        HBox item = new HBox(8);
        item.setAlignment(Pos.CENTER_LEFT);
        Rectangle swatch = new Rectangle(14, 14);
        swatch.getStyleClass().addAll("legend-swatch", colorClass);
        Label lbl = new Label(text);
        lbl.getStyleClass().add("legend-label");
        item.getChildren().addAll(swatch, lbl);
        legendBox.getChildren().add(item);
    }

    // ===================== LAYOUT LOGIC =====================

    private void layoutComponents() {
        double w = getWidth();
        double h = getHeight();

        if (w <= 0 || h <= 0)
            return;

        // Position components using percentages
        layoutPane(pcPane, PC_POS, w, h);
        layoutPane(pcAddPane, PC_ADD_POS, w, h);
        layoutPane(imPane, IM_POS, w, h);
        layoutPane(controlPane, CONTROL_POS, w, h);
        layoutPane(regPane, REG_POS, w, h);
        layoutPane(immPane, IMM_POS, w, h);
        layoutPane(aluPane, ALU_POS, w, h);
        layoutPane(dmPane, DM_POS, w, h);
        layoutPane(muxWbPane, MUX_WB_POS, w, h);

        // Legend
        legendBox.setLayoutX(w * LEGEND_POS[0] / 100);
        legendBox.setLayoutY(h * LEGEND_POS[1] / 100);

        // Recalculate wires
        layoutWires(w, h);
        layoutLabels(w, h);
    }

    private void layoutPane(StackPane pane, double[] pos, double w, double h) {
        double x = w * pos[0] / 100;
        double y = h * pos[1] / 100;
        double pw = w * pos[2] / 100;
        double ph = h * pos[3] / 100;

        pane.setLayoutX(x);
        pane.setLayoutY(y);
        pane.setPrefSize(pw, ph);

        // Update rectangle size inside the pane
        Rectangle rect = (Rectangle) pane.getChildren().get(0);
        rect.setWidth(pw);
        rect.setHeight(ph);
    }

    private void layoutWires(double w, double h) {
        // Get component bounds for wire routing
        double pcRight = pcPane.getLayoutX() + pcPane.getPrefWidth();
        double pcCenterY = pcPane.getLayoutY() + pcPane.getPrefHeight() / 2;
        double pcTop = pcPane.getLayoutY();

        double pcAddBottom = pcAddPane.getLayoutY() + pcAddPane.getPrefHeight();

        double imLeft = imPane.getLayoutX();
        double imRight = imPane.getLayoutX() + imPane.getPrefWidth();
        double imTop = imPane.getLayoutY();

        double ctrlBottom = controlPane.getLayoutY() + controlPane.getPrefHeight();

        double regLeft = regPane.getLayoutX();
        double regRight = regPane.getLayoutX() + regPane.getPrefWidth();

        double immLeft = immPane.getLayoutX();
        double immRight = immPane.getLayoutX() + immPane.getPrefWidth();
        double immCenterY = immPane.getLayoutY() + immPane.getPrefHeight() / 2;

        double aluLeft = aluPane.getLayoutX();
        double aluRight = aluPane.getLayoutX() + aluPane.getPrefWidth();
        double aluCenterY = aluPane.getLayoutY() + aluPane.getPrefHeight() / 2;

        double dmLeft = dmPane.getLayoutX();
        double dmRight = dmPane.getLayoutX() + dmPane.getPrefWidth();

        double muxLeft = muxWbPane.getLayoutX();
        double muxRight = muxWbPane.getLayoutX() + muxWbPane.getPrefWidth();
        double muxMidY = muxWbPane.getLayoutY() + muxWbPane.getPrefHeight() / 2;

        // Wire: PC -> IM (straight horizontal)
        updateWire(wirePcIm, arrowPcIm, pcRight, pcCenterY, imLeft, pcCenterY, "right");

        // Wire: PC -> PC+4 (PC goes up to PC+4 adder)
        double pcTopCenterX = pcPane.getLayoutX() + pcPane.getPrefWidth() / 2;
        updateWire(wirePcAdd, arrowPcAdd, pcTopCenterX, pcTop, pcTopCenterX, pcAddBottom, "up");

        // Wire: IM -> Control (from top of IM, goes up then right to bottom of Control)
        double imTopCenterX = imLeft + imPane.getPrefWidth() / 2;
        double ctrlBottomCenterX = controlPane.getLayoutX() + controlPane.getPrefWidth() / 2;
        double wireAboveImY = imTop - h * 0.03;
        updateWireL(wireImControl, arrowImControl,
                imTopCenterX, imTop,
                imTopCenterX, wireAboveImY,
                ctrlBottomCenterX, wireAboveImY,
                ctrlBottomCenterX, ctrlBottom, "up");

        // Wire: IM -> REG (horizontal at same height)
        double imRegY = imPane.getLayoutY() + imPane.getPrefHeight() * 0.4;
        updateWire(wireImReg, arrowImReg, imRight, imRegY, regLeft, imRegY, "right");

        // Wire: IM -> IMM (down from IM, then right to IMM)
        double imImmY = imPane.getLayoutY() + imPane.getPrefHeight() * 0.7;
        double immWireY = immCenterY;
        updateWireLV(wireImImm, arrowImImm,
                imRight, imImmY,
                imRight + w * 0.01, imImmY,
                imRight + w * 0.01, immWireY,
                immLeft, immWireY, "right");

        // Wire: REG -> ALU (A) - upper port, straight horizontal
        double regAluAY = regPane.getLayoutY() + regPane.getPrefHeight() * 0.35;
        double aluInputAY = aluPane.getLayoutY() + aluPane.getPrefHeight() * 0.3;
        updateWire(wireRegAluA, arrowRegAluA, regRight, regAluAY, aluLeft, aluInputAY, "right");

        // Wire: REG -> ALU (B) - lower port
        double regAluBY = regPane.getLayoutY() + regPane.getPrefHeight() * 0.55;
        double aluInputBY = aluPane.getLayoutY() + aluPane.getPrefHeight() * 0.7;
        double regAluBTurnX = regRight + w * 0.02;
        updateWireLV(wireRegAluB, arrowRegAluB,
                regRight, regAluBY,
                regAluBTurnX, regAluBY,
                regAluBTurnX, aluInputBY,
                aluLeft, aluInputBY, "right");

        // Wire: IMM -> ALU (B) - merges with REG->ALU B wire
        double immAluTurnX = regRight + w * 0.04;
        updateWireLV(wireImmAluB, arrowImmAluB,
                immRight, immCenterY,
                immAluTurnX, immCenterY,
                immAluTurnX, aluInputBY,
                aluLeft, aluInputBY, "right");

        // Wire: ALU -> DM (straight horizontal)
        double aluDmY = aluCenterY;
        double dmInputY = dmPane.getLayoutY() + dmPane.getPrefHeight() * 0.35;
        updateWire(wireAluDmAddr, arrowAluDmAddr, aluRight, aluDmY, dmLeft, dmInputY, "right");

        // Wire: REG -> DM (write data) - goes below components
        double regDmY = regPane.getLayoutY() + regPane.getPrefHeight() * 0.75;
        double dmWriteY = dmPane.getLayoutY() + dmPane.getPrefHeight() * 0.65;
        double belowComponentsY = h * 0.78;
        updateWireComplex(wireRegDmData, arrowRegDmData, "right",
                regRight, regDmY,
                regRight + w * 0.01, regDmY,
                regRight + w * 0.01, belowComponentsY,
                dmLeft - w * 0.01, belowComponentsY,
                dmLeft - w * 0.01, dmWriteY,
                dmLeft, dmWriteY);

        // Wire: DM -> MUX (straight horizontal)
        double dmOutY = dmPane.getLayoutY() + dmPane.getPrefHeight() * 0.35;
        double muxInTopY = muxWbPane.getLayoutY() + muxWbPane.getPrefHeight() * 0.3;
        updateWire(wireDmMux, arrowDmMux, dmRight, dmOutY, muxLeft, muxInTopY, "right");

        // Wire: ALU -> MUX (bypass) - goes above components
        double aboveComponentsY = h * 0.34;
        double muxInBottomY = muxWbPane.getLayoutY() + muxWbPane.getPrefHeight() * 0.7;
        double aluStartUpY = aluCenterY - (h * 0.03);
        updateWireComplex(wireAluMux, arrowAluMux, "right",
                aluRight, aluStartUpY,
                aluRight + w * 0.01, aluStartUpY,
                aluRight + w * 0.01, aboveComponentsY,
                muxLeft - w * 0.01, aboveComponentsY,
                muxLeft - w * 0.01, muxInBottomY,
                muxLeft, muxInBottomY);

        // Wire: WB MUX -> REG (writeback) - goes below all, back to REG
        double wbBottomY = h * 0.88;
        double wbLeftX = regLeft - w * 0.02;
        double regWriteY = regPane.getLayoutY() + regPane.getPrefHeight() * 0.8;
        updateWireComplex(wireWbReg, arrowWbReg, "right",
                muxRight, muxMidY,
                muxRight + w * 0.01, muxMidY,
                muxRight + w * 0.01, wbBottomY,
                wbLeftX, wbBottomY,
                wbLeftX, regWriteY,
                regLeft, regWriteY);
    }

    private void updateWire(Polyline wire, Polygon arrow, double x1, double y1, double x2, double y2, String arrowDir) {
        wire.getPoints().setAll(x1, y1, x2, y2);
        positionArrow(arrow, x2, y2, arrowDir);
    }

    private void updateWireL(Polyline wire, Polygon arrow, double x1, double y1, double x2, double y2, double x3,
            double y3, double x4, double y4, String arrowDir) {
        wire.getPoints().setAll(x1, y1, x2, y2, x3, y3, x4, y4);
        positionArrow(arrow, x4, y4, arrowDir);
    }

    private void updateWireLV(Polyline wire, Polygon arrow, double x1, double y1, double x2, double y2, double x3,
            double y3, double x4, double y4, String arrowDir) {
        wire.getPoints().setAll(x1, y1, x2, y2, x3, y3, x4, y4);
        positionArrow(arrow, x4, y4, arrowDir);
    }

    private void updateWireComplex(Polyline wire, Polygon arrow, String arrowDir, double... points) {
        Double[] pts = new Double[points.length];
        for (int i = 0; i < points.length; i++)
            pts[i] = points[i];
        wire.getPoints().setAll(pts);
        positionArrow(arrow, points[points.length - 2], points[points.length - 1], arrowDir);
    }

    private void positionArrow(Polygon arrow, double x, double y, String direction) {
        arrow.getPoints().clear();
        switch (direction) {
            case "right":
                arrow.getPoints().addAll(x, y - 5.0, x + 10, y, x, y + 5.0);
                break;
            case "left":
                arrow.getPoints().addAll(x, y - 5.0, x - 10, y, x, y + 5.0);
                break;
            case "up":
                arrow.getPoints().addAll(x - 5.0, y, x, y - 10, x + 5.0, y);
                break;
            case "down":
                arrow.getPoints().addAll(x - 5.0, y, x, y + 10, x + 5.0, y);
                break;
        }
    }

    private void layoutLabels(double w, double h) {
        // PC label
        txtPC.setLayoutX(pcPane.getLayoutX());
        txtPC.setLayoutY(pcPane.getLayoutY() - 10);

        // Instruction type label
        txtInstruction.setLayoutX(imPane.getLayoutX());
        txtInstruction.setLayoutY(imPane.getLayoutY() + imPane.getPrefHeight() + 15);

        // Control signals
        txtControlSignals.setLayoutX(w * 0.05);
        txtControlSignals.setLayoutY(h * 0.93);

        // Control signal labels (next to control unit)
        double ctrlRight = controlPane.getLayoutX() + controlPane.getPrefWidth() + 5;
        lblRegWrite.setLayoutX(ctrlRight);
        lblRegWrite.setLayoutY(controlPane.getLayoutY() + 5);
        lblMemRead.setLayoutX(ctrlRight);
        lblMemRead.setLayoutY(controlPane.getLayoutY() + 20);
        lblMemWrite.setLayoutX(ctrlRight);
        lblMemWrite.setLayoutY(controlPane.getLayoutY() + 35);
        lblALUSrc.setLayoutX(ctrlRight);
        lblALUSrc.setLayoutY(controlPane.getLayoutY() + 50);

        // Value labels
        txtRs1Val.setLayoutX(regRight() + 5);
        txtRs1Val.setLayoutY(regPane.getLayoutY() + regPane.getPrefHeight() * 0.25);
        txtRs2Val.setLayoutX(regRight() + 5);
        txtRs2Val.setLayoutY(regPane.getLayoutY() + regPane.getPrefHeight() * 0.5);
        txtAluResult.setLayoutX(aluPane.getLayoutX() + aluPane.getPrefWidth() + 5);
        txtAluResult.setLayoutY(aluPane.getLayoutY() + aluPane.getPrefHeight() / 2);

        // Component title labels (below components)
        lblPcTitle.setLayoutX(pcPane.getLayoutX());
        lblPcTitle.setLayoutY(pcPane.getLayoutY() + pcPane.getPrefHeight() + 5);
        lblImTitle.setLayoutX(imPane.getLayoutX() + imPane.getPrefWidth() / 3);
        lblImTitle.setLayoutY(imPane.getLayoutY() + imPane.getPrefHeight() + 5);
        lblRegTitle.setLayoutX(regPane.getLayoutX() + regPane.getPrefWidth() / 4);
        lblRegTitle.setLayoutY(regPane.getLayoutY() + regPane.getPrefHeight() + 5);
        lblImmTitle.setLayoutX(immPane.getLayoutX());
        lblImmTitle.setLayoutY(immPane.getLayoutY() + immPane.getPrefHeight() + 5);
        lblAluTitle.setLayoutX(aluPane.getLayoutX());
        lblAluTitle.setLayoutY(aluPane.getLayoutY() + aluPane.getPrefHeight() + 5);
        lblDmTitle.setLayoutX(dmPane.getLayoutX() + dmPane.getPrefWidth() / 3);
        lblDmTitle.setLayoutY(dmPane.getLayoutY() + dmPane.getPrefHeight() + 5);
        lblMuxTitle.setLayoutX(muxWbPane.getLayoutX() - 10);
        lblMuxTitle.setLayoutY(muxWbPane.getLayoutY() + muxWbPane.getPrefHeight() + 5);
    }

    private double regRight() {
        return regPane.getLayoutX() + regPane.getPrefWidth();
    }

    // ===================== INITIALIZATION =====================

    private void initializeMaps() {
        components.put("PC", compPc);
        components.put("IM", compIm);
        components.put("REG", compReg);
        components.put("IMM", compImm);
        components.put("ALU", compAlu);
        components.put("DM", compDm);
        components.put("MUX_WB", compMuxWb);
        components.put("CONTROL", compControl);
        components.put("PC_ADD", compPcAdd);

        wires.put("PC_IM", wirePcIm);
        wires.put("IM_REG", wireImReg);
        wires.put("IM_IMM", wireImImm);
        wires.put("REG_ALU_A", wireRegAluA);
        wires.put("REG_ALU_B", wireRegAluB);
        wires.put("IMM_ALU_B", wireImmAluB);
        wires.put("ALU_DM_ADDR", wireAluDmAddr);
        wires.put("REG_DM_DATA", wireRegDmData);
        wires.put("DM_MUX", wireDmMux);
        wires.put("ALU_MUX", wireAluMux);
        wires.put("WB_REG", wireWbReg);

        arrows.put("PC_IM", arrowPcIm);
        arrows.put("IM_REG", arrowImReg);
        arrows.put("IM_IMM", arrowImImm);
        arrows.put("REG_ALU_A", arrowRegAluA);
        arrows.put("REG_ALU_B", arrowRegAluB);
        arrows.put("IMM_ALU_B", arrowImmAluB);
        arrows.put("ALU_DM_ADDR", arrowAluDmAddr);
        arrows.put("REG_DM_DATA", arrowRegDmData);
        arrows.put("DM_MUX", arrowDmMux);
        arrows.put("ALU_MUX", arrowAluMux);
        arrows.put("WB_REG", arrowWbReg);
    }

    private void initializeTooltips() {
        if (compPc != null) {
            Tooltip.install(compPc.getParent(), new Tooltip(
                    "Program Counter (PC)\n" +
                            "Holds the address of the current instruction.\n" +
                            "Incremented by 4 after each instruction (or set by branch/jump)."));
        }
        if (compIm != null) {
            Tooltip.install(compIm.getParent(), new Tooltip(
                    "Instruction Memory (I-Mem)\n" +
                            "Read-only memory storing the program instructions.\n" +
                            "Addressed by PC, outputs 32-bit instruction word."));
        }
        if (compReg != null) {
            Tooltip.install(compReg.getParent(), new Tooltip(
                    "Register File\n" +
                            "32 general-purpose registers (x0-x31).\n" +
                            "x0 is hardwired to 0.\n" +
                            "Two read ports (rs1, rs2) and one write port (rd)."));
        }
        if (compImm != null) {
            Tooltip.install(compImm.getParent(), new Tooltip(
                    "Immediate Generator\n" +
                            "Extracts and sign-extends immediate values from instructions.\n" +
                            "Supports I, S, B, U, and J-type immediate formats."));
        }
        if (compAlu != null) {
            Tooltip.install(compAlu.getParent(), new Tooltip(
                    "Arithmetic Logic Unit (ALU)\n" +
                            "Performs arithmetic and logical operations.\n" +
                            "Operations: ADD, SUB, AND, OR, XOR, SLT, shifts, etc."));
        }
        if (compDm != null) {
            Tooltip.install(compDm.getParent(), new Tooltip(
                    "Data Memory (D-Mem)\n" +
                            "Read/write memory for load and store instructions.\n" +
                            "Addressed by ALU result, data from/to register file."));
        }
        if (compMuxWb != null) {
            Tooltip.install(compMuxWb.getParent(), new Tooltip(
                    "Write-Back Multiplexer\n" +
                            "Selects data to write back to registers.\n" +
                            "Sources: ALU result, Memory read data, or PC+4."));
        }
        if (compControl != null) {
            Tooltip.install(compControl.getParent(), new Tooltip(
                    "Control Unit\n" +
                            "Decodes the instruction opcode and generates control signals.\n" +
                            "Outputs: RegWrite, MemRead, MemWrite, ALUSrc, Branch, etc.\n" +
                            "This is the 'brain' that tells other components what to do."));
        }
        if (compPcAdd != null) {
            Tooltip.install(compPcAdd.getParent(), new Tooltip(
                    "PC + 4 Adder\n" +
                            "Calculates the address of the next sequential instruction.\n" +
                            "RISC-V uses 32-bit (4-byte) instructions, so PC increments by 4."));
        }
    }

    private void setupZoomAndPan() {
        Scale scaleTransform = new Scale(1, 1, 0, 0);
        this.getTransforms().add(scaleTransform);

        this.setOnScroll(event -> {
            event.consume();
            double zoomFactor = 1.1;
            double deltaY = event.getDeltaY();
            if (deltaY < 0) {
                zoomFactor = 1 / zoomFactor;
            }
            double scale = scaleTransform.getX() * zoomFactor;
            if (scale > 0.1 && scale < 5.0) {
                scaleTransform.setX(scale);
                scaleTransform.setY(scale);
            }
        });

        this.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                this.setTranslateX(0);
                this.setTranslateY(0);
                scaleTransform.setX(1);
                scaleTransform.setY(1);
            }
        });

        final double[] lastMouse = new double[2];
        this.setOnMousePressed(event -> {
            lastMouse[0] = event.getSceneX();
            lastMouse[1] = event.getSceneY();
        });

        this.setOnMouseDragged(event -> {
            double deltaX = event.getSceneX() - lastMouse[0];
            double deltaY = event.getSceneY() - lastMouse[1];
            this.setTranslateX(this.getTranslateX() + deltaX);
            this.setTranslateY(this.getTranslateY() + deltaY);
            lastMouse[0] = event.getSceneX();
            lastMouse[1] = event.getSceneY();
        });
    }

    // ===================== UPDATE LOGIC =====================

    public void update() {
        if (txtPC != null) {
            txtPC.setText("PC: " + String.format("0x%04X", cpu.getProgramCounter()));
            txtPC.setVisible(true);
        }

        InstructionDecoded inst = cpu.getLastDecodedInstruction();
        if (inst == null) {
            resetHighlights();
            if (txtInstruction != null)
                txtInstruction.setText("Type: --");
            return;
        }

        resetHighlights();
        highlight("PC");
        highlight("PC_IM");
        highlight("IM");
        highlight("IM_REG");

        int opcode = inst.getOpcode();

        boolean isLoad = (opcode == 0b0000011);
        boolean isStore = (opcode == 0b0100011);
        boolean isRType = (opcode == 0b0110011);
        boolean isIType = (opcode == 0b0010011);
        boolean isBranch = (opcode == 0b1100011);
        boolean isJal = (opcode == 0b1101111);
        boolean isJalr = (opcode == 0b1100111);
        boolean isLui = (opcode == 0b0110111);
        boolean isAuipc = (opcode == 0b0010111);
        boolean isSystem = (opcode == 0b1110011);
        boolean isFence = (opcode == 0b0001111);

        highlight("CONTROL");
        updateControlSignals(isLoad, isStore, isRType, isIType, isBranch, isJal, isJalr, isLui, isAuipc);

        String typeStr = "UNKNOWN";

        if (isRType) {
            typeStr = "R-Type";
            highlight("REG");
            highlight("REG_ALU_A");
            animateDataFlow("REG_ALU_A");
            highlight("REG_ALU_B");
            animateDataFlow("REG_ALU_B");
            highlight("ALU");
            highlight("ALU_MUX");
            animateDataFlow("ALU_MUX");
            highlight("MUX_WB");
            highlight("WB_REG");
            animateDataFlow("WB_REG");
        } else if (isIType) {
            typeStr = "I-Type";
            highlight("REG");
            highlight("IM_IMM");
            highlight("IMM");
            highlight("REG_ALU_A");
            animateDataFlow("REG_ALU_A");
            highlight("IMM_ALU_B");
            animateDataFlow("IMM_ALU_B");
            highlight("ALU");
            highlight("ALU_MUX");
            animateDataFlow("ALU_MUX");
            highlight("MUX_WB");
            highlight("WB_REG");
            animateDataFlow("WB_REG");
        } else if (isLoad) {
            typeStr = "LOAD";
            highlight("REG");
            highlight("IM_IMM");
            highlight("IMM");
            highlight("REG_ALU_A");
            animateDataFlow("REG_ALU_A");
            highlight("IMM_ALU_B");
            animateDataFlow("IMM_ALU_B");
            highlight("ALU");
            highlight("ALU_DM_ADDR");
            animateDataFlow("ALU_DM_ADDR");
            highlight("DM");
            highlight("DM_MUX");
            animateDataFlow("DM_MUX");
            highlight("MUX_WB");
            highlight("WB_REG");
            animateDataFlow("WB_REG");
        } else if (isStore) {
            typeStr = "STORE";
            highlight("REG");
            highlight("IM_IMM");
            highlight("IMM");
            highlight("REG_ALU_A");
            animateDataFlow("REG_ALU_A");
            highlight("IMM_ALU_B");
            animateDataFlow("IMM_ALU_B");
            highlight("ALU");
            highlight("ALU_DM_ADDR");
            animateDataFlow("ALU_DM_ADDR");
            highlight("REG_DM_DATA");
            animateDataFlow("REG_DM_DATA");
            highlight("DM");
        } else if (isBranch) {
            typeStr = "BRANCH";
            highlight("REG");
            highlight("IM_IMM");
            highlight("IMM");
            highlight("REG_ALU_A");
            animateDataFlow("REG_ALU_A");
            highlight("REG_ALU_B");
            animateDataFlow("REG_ALU_B");
            highlight("ALU");
        } else if (isJal) {
            typeStr = "JAL";
            highlight("IM_IMM");
            highlight("IMM");
            highlight("MUX_WB");
            highlight("WB_REG");
            animateDataFlow("WB_REG");
        } else if (isJalr) {
            typeStr = "JALR";
            highlight("REG");
            highlight("IM_IMM");
            highlight("IMM");
            highlight("REG_ALU_A");
            animateDataFlow("REG_ALU_A");
            highlight("IMM_ALU_B");
            animateDataFlow("IMM_ALU_B");
            highlight("ALU");
            highlight("MUX_WB");
            highlight("WB_REG");
            animateDataFlow("WB_REG");
        } else if (isLui) {
            typeStr = "LUI";
            highlight("IM_IMM");
            highlight("IMM");
            highlight("MUX_WB");
            highlight("WB_REG");
            animateDataFlow("WB_REG");
        } else if (isAuipc) {
            typeStr = "AUIPC";
            highlight("IM_IMM");
            highlight("IMM");
            highlight("ALU");
            highlight("ALU_MUX");
            animateDataFlow("ALU_MUX");
            highlight("MUX_WB");
            highlight("WB_REG");
            animateDataFlow("WB_REG");
        } else if (isSystem) {
            typeStr = "SYSTEM";
        } else if (isFence) {
            typeStr = "FENCE";
        }

        if (txtInstruction != null)
            txtInstruction.setText("Type: " + typeStr);
        if (txtControlSignals != null)
            txtControlSignals.setText(String.format("Op: 0x%02X | Rd: x%d | Rs1: x%d | Rs2: x%d", opcode, inst.getRd(),
                    inst.getRs1(), inst.getRs2()));

        if (txtRs1Val != null) {
            txtRs1Val.setText(String.format("0x%08X", cpu.lastRs1Val));
            txtRs1Val.setVisible(true);
        }
        if (txtRs2Val != null) {
            txtRs2Val.setText(String.format("0x%08X", cpu.lastRs2Val));
            txtRs2Val.setVisible(true);
        }
        if (txtAluResult != null) {
            txtAluResult.setText(String.format("0x%08X", cpu.lastAluResult));
            txtAluResult.setVisible(true);
        }
    }

    private void resetHighlights() {
        for (Shape s : components.values()) {
            if (s != null) {
                s.getStyleClass().remove("datapath-active");
                if (!s.getStyleClass().contains("datapath-component")) {
                    s.getStyleClass().add("datapath-component");
                }
            }
        }
        for (Shape s : wires.values()) {
            if (s != null) {
                s.getStyleClass().remove("datapath-active");
                if (!s.getStyleClass().contains("wire-inactive")) {
                    s.getStyleClass().add("wire-inactive");
                }
            }
        }
        for (Polygon arrow : arrows.values()) {
            if (arrow != null) {
                arrow.getStyleClass().remove("arrow-active");
                if (!arrow.getStyleClass().contains("arrow-head")) {
                    arrow.getStyleClass().add("arrow-head");
                }
            }
        }
    }

    private void animateDataFlow(String wireId) {
        Shape wire = wires.get(wireId);
        if (!(wire instanceof Polyline))
            return;

        Circle packet = new Circle(5, Color.GOLD);
        packet.setStroke(Color.ORANGE);
        packet.setStrokeWidth(2);
        this.getChildren().add(packet);

        PathTransition pathTransition = new PathTransition();
        pathTransition.setDuration(Duration.millis(600));
        pathTransition.setPath(wire);
        pathTransition.setNode(packet);
        pathTransition.setCycleCount(1);
        pathTransition.setOnFinished(e -> this.getChildren().remove(packet));
        pathTransition.play();
    }

    private void highlight(String id) {
        Shape s = components.getOrDefault(id, wires.get(id));
        if (s != null && !s.getStyleClass().contains("datapath-active")) {
            s.getStyleClass().add("datapath-active");
        }

        Polygon arrow = arrows.get(id);
        if (arrow != null && !arrow.getStyleClass().contains("arrow-active")) {
            arrow.getStyleClass().add("arrow-active");
        }
    }

    private void updateControlSignals(boolean isLoad, boolean isStore, boolean isRType,
            boolean isIType, boolean isBranch, boolean isJal, boolean isJalr,
            boolean isLui, boolean isAuipc) {

        boolean regWrite = isRType || isIType || isLoad || isJal || isJalr || isLui || isAuipc;
        boolean memRead = isLoad;
        boolean memWrite = isStore;
        boolean aluSrc = isIType || isLoad || isStore || isJalr || isAuipc;

        setControlSignalStyle(lblRegWrite, regWrite);
        setControlSignalStyle(lblMemRead, memRead);
        setControlSignalStyle(lblMemWrite, memWrite);
        setControlSignalStyle(lblALUSrc, aluSrc);
    }

    private void setControlSignalStyle(Label label, boolean active) {
        if (label == null)
            return;

        label.getStyleClass().removeAll("control-signal-active", "control-signal-inactive");
        if (active) {
            label.getStyleClass().add("control-signal-active");
        } else {
            label.getStyleClass().add("control-signal-inactive");
        }
    }
}
