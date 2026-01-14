package cse311.gui.components;

import cse311.InstructionDecoded;
import cse311.RV32Cpu;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;
import java.util.HashMap;
import java.util.Map;

public class DatapathView extends StackPane {

    private final RV32Cpu cpu;
    private Pane diagramPane;
    private ScrollPane scrollPane;
    private Group zoomGroup;

    // Config
    private static final double START_X = 50;
    private static final double START_Y = 100;
    private static final Color COLOR_INACTIVE = Color.LIGHTGRAY;
    private static final Color COLOR_ACTIVE = Color.web("#e74c3c"); // Red highlight
    private static final Color COLOR_COMPONENT = Color.web("#ecf0f1");
    private static final Color COLOR_STROKE = Color.web("#2c3e50");

    // Dynamic Elements MAP (Name -> Shape) to toggle colors
    private Map<String, Shape> components = new HashMap<>();
    private Map<String, Shape> wires = new HashMap<>();

    // Text labels for dynamic values (PC, ALU Result, etc.)
    private Text txtPC;
    private Text txtInstruction;
    private Text txtControlSignals;

    public DatapathView(RV32Cpu cpu) {
        this.cpu = cpu;
        initializeLayout();
        setupZoomAndPan();
    }

    private void initializeLayout() {
        this.setStyle("-fx-background-color: #ffffff;");

        // Create the container for the drawing
        diagramPane = new Pane();
        diagramPane.setPrefSize(1200, 800); // Standard schematic size
        diagramPane.setStyle("-fx-background-color: #ffffff;");

        // --- DRAW COMPONENTS (On diagramPane) ---

        // 1. PC
        createBlock("PC", 50, 250, 40, 60);
        txtPC = createDynamicText("PC: --", 55, 285);

        // 2. Instruction Memory
        createBlock("IM", 150, 200, 100, 150);
        createLabel("Inst Mem", 160, 220);
        txtInstruction = createDynamicText("Inst: --", 160, 280);

        // 3. Register File
        createBlock("REG", 350, 200, 100, 150);
        createLabel("Registers", 360, 220);

        // 4. Imm Gen
        createBlock("IMM", 350, 400, 80, 60);
        createLabel("Imm Gen", 360, 435);

        // 5. ALU
        // ALU is usually a trapezoid, but rectangle for now
        createBlock("ALU", 550, 230, 80, 100);
        createLabel("ALU", 575, 280);

        // 6. Data Memory
        createBlock("DM", 700, 200, 100, 150);
        createLabel("Data Mem", 710, 220);

        // 7. Write Back Mux (Result Mux)
        createBlock("MUX_WB", 850, 250, 30, 60);

        // --- DRAW WIRES (Simplified) ---
        // PC -> IM
        createWire("PC_IM", 90, 280, 150, 280);

        // IM -> Reg (Read Reg 1 & 2 & Write Reg)
        // Usually split into rs1, rs2, rd. We simplify to one bus.
        createWire("IM_REG", 250, 280, 350, 280);

        // IM -> Imm Gen
        createWire("IM_IMM", 250, 280, 280, 280, 280, 430, 350, 430); // Elbow

        // Reg -> ALU (A)
        createWire("REG_ALU_A", 450, 250, 550, 250);

        // Reg -> ALU (B) / Imm -> ALU (B) (Via Mux, simplified)
        createWire("REG_ALU_B", 450, 310, 500, 310, 500, 300, 550, 300);
        createWire("IMM_ALU_B", 430, 430, 500, 430, 500, 300); // Merges with B input path

        // ALU -> DM (Address)
        createWire("ALU_DM_ADDR", 630, 280, 700, 280);

        // Reg -> DM (Write Data)
        createWire("REG_DM_DATA", 450, 330, 480, 330, 480, 380, 680, 380, 680, 330, 700, 330);

        // DM -> Mux
        createWire("DM_MUX", 800, 280, 850, 250 + 10);

        // ALU -> Mux
        createWire("ALU_MUX", 630, 280, 660, 280, 660, 180, 830, 180, 830, 250 + 40, 850, 250 + 40); // Long path around

        // Mux -> Reg (Write Back)
        createWire("WB_REG", 880, 280, 920, 280, 920, 550, 300, 550, 300, 300, 350, 300); // Big loop back

        // --- ADD TO PANE ---
        this.txtControlSignals = new Text(50, 550, "Control: ");
        this.txtControlSignals.setFont(Font.font("Monospace", 14));
        diagramPane.getChildren().add(txtControlSignals);
    }

    private void setupZoomAndPan() {
        // Wrap diagram in Group for transforms
        zoomGroup = new Group(diagramPane);

        // Wrap logic in a Panner using StackPane
        // We use a simple Pan/Zoom logic without ScrollPane bars for cleaner "CAD"
        // feel,
        // or we use ScrollPane if we want scrollbars.
        // Let's use a "Infinite Canvas" approach with Pane and transforms.

        Pane canvasContainer = new Pane();
        canvasContainer.getChildren().add(zoomGroup);
        // Clip content
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(this.widthProperty());
        clip.heightProperty().bind(this.heightProperty());
        this.setClip(clip);

        this.getChildren().add(canvasContainer);

        // Zoom Logic
        Scale scaleTransform = new Scale(1, 1, 0, 0);
        zoomGroup.getTransforms().add(scaleTransform);

        this.setOnScroll(event -> {
            event.consume();

            double zoomFactor = 1.1;
            double deltaY = event.getDeltaY();
            if (deltaY < 0) {
                zoomFactor = 1 / zoomFactor;
            }

            // Zoom centered on mouse
            double scale = scaleTransform.getX() * zoomFactor;

            // Limit zoom
            if (scale > 0.1 && scale < 5.0) {
                scaleTransform.setX(scale);
                scaleTransform.setY(scale);

                // Adjust pivot?? Simpler to just scale, then pan manually if needed.
                // Correct logic for pivot scaling:
                // https://stackoverflow.com/questions/30679025/graph-visualisation-like-yfiles-in-javafx
            }
        });

        // Pan Logic
        final double[] lastMouse = new double[2];

        this.setOnMousePressed(event -> {
            lastMouse[0] = event.getX();
            lastMouse[1] = event.getY();
        });

        this.setOnMouseDragged(event -> {
            double deltaX = event.getX() - lastMouse[0];
            double deltaY = event.getY() - lastMouse[1];

            zoomGroup.setTranslateX(zoomGroup.getTranslateX() + deltaX);
            zoomGroup.setTranslateY(zoomGroup.getTranslateY() + deltaY);

            lastMouse[0] = event.getX();
            lastMouse[1] = event.getY();
        });
    }

    private void createBlock(String id, double x, double y, double w, double h) {
        Rectangle rect = new Rectangle(x, y, w, h);
        rect.setFill(COLOR_COMPONENT);
        rect.setStroke(COLOR_STROKE);
        rect.setStrokeWidth(2);
        diagramPane.getChildren().add(rect);
        components.put(id, rect);
    }

    private void createLabel(String text, double x, double y) {
        Text t = new Text(x, y, text);
        t.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        diagramPane.getChildren().add(t);
    }

    private Text createDynamicText(String initial, double x, double y) {
        Text t = new Text(x, y, initial);
        t.setFont(Font.font("Monospace", 10));
        diagramPane.getChildren().add(t);
        return t;
    }

    private void createWire(String id, double... coords) {
        Polyline line = new Polyline(coords);
        line.setStroke(COLOR_INACTIVE);
        line.setStrokeWidth(3);
        diagramPane.getChildren().add(line);
        line.toBack(); // Draw behind blocks
        wires.put(id, line);
    }

    // --- UPDATE LOGIC ---

    public void update() {
        txtPC.setText("PC: " + String.format("0x%04X", cpu.getProgramCounter()));

        InstructionDecoded inst = cpu.getLastDecodedInstruction();
        if (inst == null) {
            resetHighlights();
            txtInstruction.setText("Inst: --");
            return;
        }

        // Identify Instruction Type and Name
        // We can't easily get the ASM string without disassembling again,
        // effectively we are guessing or need the Disassembler.
        // For now, let's just show opcode/type.

        resetHighlights();
        highlight("PC");
        highlight("PC_IM");
        highlight("IM");
        // Always read instructions
        highlight("IM_REG");

        int opcode = inst.getOpcode();

        // Logic to highlight paths
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
            highlight("REG_ALU_B");
            highlight("ALU");
            highlight("ALU_MUX"); // Loop back
            highlight("MUX_WB");
            highlight("WB_REG");
        } else if (isIType) {
            typeStr = "I-Type";
            highlight("REG");
            highlight("IM_IMM");
            highlight("IMM");
            highlight("REG_ALU_A");
            highlight("IMM_ALU_B");
            highlight("ALU");
            highlight("ALU_MUX");
            highlight("MUX_WB");
            highlight("WB_REG");
        } else if (isLoad) {
            typeStr = "LOAD";
            highlight("REG");
            highlight("IM_IMM");
            highlight("IMM");
            highlight("REG_ALU_A");
            highlight("IMM_ALU_B");
            highlight("ALU");
            highlight("ALU_DM_ADDR");
            highlight("DM");
            highlight("DM_MUX");
            highlight("MUX_WB");
            highlight("WB_REG");
        } else if (isStore) {
            typeStr = "STORE";
            highlight("REG");
            highlight("IM_IMM");
            highlight("IMM");
            highlight("REG_ALU_A");
            highlight("IMM_ALU_B");
            highlight("ALU");
            highlight("ALU_DM_ADDR");
            highlight("REG_DM_DATA");
            highlight("DM");
            // No WB
        } else if (isBranch) {
            typeStr = "BRANCH";
            highlight("REG");
            highlight("IM_IMM");
            highlight("IMM");
            highlight("REG_ALU_A");
            highlight("REG_ALU_B");
            highlight("ALU");
            // Branch doesn't write back to reg in this simple view usually, it updates PC.
            // We didn't draw the PC Mux fully, but we can highlight ALU as it computes
            // condition.
        } else if (isJal) {
            typeStr = "JAL";
            // Writes PC+4 to Reg (Not strictly following ALU path in some archs, but
            // usually passes PC through ALU or adder)
            highlight("MUX_WB");
            highlight("WB_REG");
        }

        txtInstruction.setText("Type: " + typeStr);
        txtControlSignals.setText(String.format("Op: 0x%02X | Rd: %d | Rs1: %d | Rs2: %d", opcode, inst.getRd(),
                inst.getRs1(), inst.getRs2()));
    }

    private void resetHighlights() {
        for (Shape s : components.values()) {
            s.setFill(COLOR_COMPONENT);
            s.setStroke(COLOR_STROKE);
        }
        for (Shape s : wires.values()) {
            s.setStroke(COLOR_INACTIVE);
            s.setStrokeWidth(3);
        }
    }

    private void highlight(String id) {
        if (components.containsKey(id)) {
            components.get(id).setStroke(COLOR_ACTIVE);
            // components.get(id).setFill(Color.web("#fdebd0"));
        }
        if (wires.containsKey(id)) {
            wires.get(id).setStroke(COLOR_ACTIVE);
            wires.get(id).setStrokeWidth(5);
        }
    }
}
