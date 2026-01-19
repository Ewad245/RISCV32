package cse311.gui.components;

import cse311.InstructionDecoded;
import cse311.RV32Cpu;
import javafx.animation.PathTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DatapathView extends AnchorPane {

    private RV32Cpu cpu;

    public void setCpu(RV32Cpu cpu) {
        this.cpu = cpu;
        update();
    }

    // Dynamic Elements MAP (Name -> Shape) to toggle classes
    private final Map<String, Shape> components = new HashMap<>();
    private final Map<String, Shape> wires = new HashMap<>();

    // --- FXML Injected Components ---
    @FXML
    private Rectangle compPc;
    @FXML
    private Rectangle compIm;
    @FXML
    private Rectangle compReg;
    @FXML
    private Rectangle compImm;
    @FXML
    private Rectangle compAlu;
    @FXML
    private Rectangle compDm;
    @FXML
    private Rectangle compMuxWb;

    // --- FXML Injected Wires ---
    @FXML
    private Polyline wirePcIm;
    @FXML
    private Polyline wireImReg;
    @FXML
    private Polyline wireImImm;
    @FXML
    private Polyline wireRegAluA;
    @FXML
    private Polyline wireRegAluB;
    @FXML
    private Polyline wireImmAluB;
    @FXML
    private Polyline wireAluDmAddr;
    @FXML
    private Polyline wireRegDmData;
    @FXML
    private Polyline wireDmMux;
    @FXML
    private Polyline wireAluMux;
    @FXML
    private Polyline wireWbReg;

    // --- FXML Injected Labels ---
    @FXML
    private Text txtPC;
    @FXML
    private Text txtInstruction;
    @FXML
    private Text txtControlSignals;
    @FXML
    private Text txtRs1Val;
    @FXML
    private Text txtRs2Val;
    @FXML
    private Text txtAluResult;

    public DatapathView(RV32Cpu cpu) {
        this.cpu = cpu;

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/DatapathView.fxml"));
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load DatapathView.fxml", e);
        }

        initializeMaps();
        setupZoomAndPan();
    }

    private void initializeMaps() {
        components.put("PC", compPc);
        components.put("IM", compIm);
        components.put("REG", compReg);
        components.put("IMM", compImm);
        components.put("ALU", compAlu);
        components.put("DM", compDm);
        components.put("MUX_WB", compMuxWb);

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

            // Limit zoom
            if (scale > 0.1 && scale < 5.0) {
                scaleTransform.setX(scale);
                scaleTransform.setY(scale);
            }
        });

        // Double click to reset
        this.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                this.setTranslateX(0);
                this.setTranslateY(0);
                scaleTransform.setX(1);
                scaleTransform.setY(1);
            }
        });

        // Pan Logic
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

    // --- UPDATE LOGIC ---

    public void update() {
        if (txtPC != null) {
            txtPC.setText("PC: " + String.format("0x%04X", cpu.getProgramCounter()));
            txtPC.setVisible(true);
        }

        InstructionDecoded inst = cpu.getLastDecodedInstruction();
        if (inst == null) {
            resetHighlights();
            if (txtInstruction != null)
                txtInstruction.setText("Inst: --");
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
            highlight("MUX_WB");
            highlight("WB_REG");
            animateDataFlow("WB_REG");
        }

        if (txtInstruction != null)
            txtInstruction.setText("Type: " + typeStr);
        if (txtControlSignals != null)
            txtControlSignals.setText(String.format("Op: 0x%02X | Rd: %d | Rs1: %d | Rs2: %d", opcode, inst.getRd(),
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
                // Remove active class
                s.getStyleClass().remove("datapath-active");
                // Ensure base class is present
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
    }

    private void animateDataFlow(String wireId) {
        Shape wire = wires.get(wireId);
        if (!(wire instanceof Polyline))
            return;

        // Create a "data packet" dot
        Circle packet = new Circle(4, Color.GOLD);
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
        if (s == null)
            return;

        // Using CSS classes for highlighting
        // We accumulate specific helper classes if needed (like datapath-pc),
        // but 'datapath-active' is the main one for stroke color/width.

        if (!s.getStyleClass().contains("datapath-active")) {
            s.getStyleClass().add("datapath-active");
        }
    }
}
