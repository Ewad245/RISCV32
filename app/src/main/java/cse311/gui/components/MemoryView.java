package cse311.gui.components;

import cse311.MemoryManager;
import cse311.kernel.memory.ProcessMemoryCoordinator;
import cse311.kernel.contiguous.ContiguousMemoryManager;
import cse311.kernel.contiguous.MemoryBlock;
import cse311.kernel.contiguous.ProcessBlock;
import cse311.kernel.NonContiguous.paging.PagedMemoryManager;
import cse311.kernel.NonContiguous.paging.FrameOwner;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import java.util.List;

public class MemoryView extends Pane {

    private final MemoryManager memory;
    private final Canvas canvas;
    private final boolean isContiguous;

    public MemoryView(MemoryManager memory, ProcessMemoryCoordinator coordinator) {
        this.memory = memory;
        this.isContiguous = (memory instanceof ContiguousMemoryManager);

        // Canvas that resizes with the pane
        this.canvas = new Canvas(800, 200);
        this.getChildren().add(canvas);

        // Tooltip
        Tooltip tooltip = new Tooltip();
        Tooltip.install(canvas, tooltip);

        // Mouse Move Listener for Tooltip
        canvas.setOnMouseMoved(e -> handleMouseMove(e, tooltip));

        // Resize listener
        this.widthProperty().addListener(e -> draw());
        this.heightProperty().addListener(e -> draw());
    }

    public void update() {
        draw();
    }

    private void draw() {
        double w = getWidth();
        double h = getHeight();
        if (w == 0 || h == 0)
            return;

        canvas.setWidth(w);
        canvas.setHeight(h);

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        if (isContiguous) {
            drawContiguous(gc, w, h);
        } else if (memory instanceof PagedMemoryManager) {
            drawPaging(gc, w, h);
        } else {
            gc.setFill(Color.BLACK);
            gc.fillText("Unknown Memory Manager", 10, 20);
        }
    }

    private void drawContiguous(GraphicsContext gc, double w, double h) {
        ContiguousMemoryManager cmm = (ContiguousMemoryManager) memory;

        // Total Memory Size (assuming we can get it or just use the end of last block)
        // Hardcoded for now based on RV32Computer (12MB)
        int totalMem = 12 * 1024 * 1024; // 12MB

        double scale = w / totalMem;
        double barHeight = 60;
        double y = 50;

        // Draw Base (Empty)
        gc.setFill(Color.LIGHTGRAY);
        gc.fillRect(0, y, w, barHeight);
        gc.setStroke(Color.BLACK);
        gc.strokeRect(0, y, w, barHeight);

        // Draw Fragments (Free Blocks) in GREEN
        List<MemoryBlock> freeList = cmm.getFreeBlocks();
        gc.setFill(Color.LIGHTGREEN);
        for (MemoryBlock b : freeList) {
            double x = b.start * scale;
            double bw = b.size * scale;
            gc.fillRect(x, y, bw, barHeight);
            gc.strokeRect(x, y, bw, barHeight);
        }

        // Draw Allocations (Process Blocks) in RED/BLUE
        List<ProcessBlock> allocatedList = cmm.getAllocatedBlocks();
        Color[] colors = { Color.SALMON, Color.LIGHTBLUE, Color.ORANGE, Color.VIOLET, Color.CYAN };

        for (ProcessBlock pb : allocatedList) {
            double x = pb.start * scale;
            double bw = pb.size * scale;

            gc.setFill(colors[pb.pid % colors.length]);
            gc.fillRect(x, y, bw, barHeight);

            // Text Label
            if (bw > 20) {
                gc.setFill(Color.BLACK);
                gc.fillText("P" + pb.pid, x + 2, y + barHeight / 2 + 5);
            }
        }

        gc.setFill(Color.BLACK);
        gc.fillText("Contiguous Memory (Tape View) - Total: 12MB", 10, 30);
    }

    private void drawPaging(GraphicsContext gc, double w, double h) {
        PagedMemoryManager pmm = (PagedMemoryManager) memory;
        FrameOwner[] frames = pmm.getFrameOwners();
        int totalFrames = pmm.getTotalFrames();

        // Calculate grid dimensions
        // Each frame is a small square
        double boxSize = 15;
        double gap = 1;
        double startY = 40;

        int cols = (int) (w / (boxSize + gap));
        if (cols < 1)
            cols = 1;

        Color[] colors = { Color.SALMON, Color.LIGHTBLUE, Color.ORANGE, Color.VIOLET, Color.CYAN, Color.GOLD,
                Color.PINK };

        for (int i = 0; i < totalFrames; i++) {
            int row = i / cols;
            int col = i % cols;

            double x = col * (boxSize + gap);
            double y = startY + row * (boxSize + gap);

            // Determine Color
            FrameOwner owner = frames[i];
            if (owner == null) {
                gc.setFill(Color.LIGHTGRAY); // Free
            } else if (owner.pid == -1) {
                gc.setFill(Color.DARKGRAY); // Page Table Frame
            } else {
                gc.setFill(colors[owner.pid % colors.length]); // Allocated to PID
            }

            gc.fillRect(x, y, boxSize, boxSize);

            // Optional: Draw border for allocated
            if (owner != null) {
                gc.setStroke(Color.BLACK);
                gc.setLineWidth(0.5);
                gc.strokeRect(x, y, boxSize, boxSize);
            }
        }

        gc.setFill(Color.BLACK);
        gc.fillText("Paging Mode: " + totalFrames + " Frames (4KB each)", 10, 20);
    }

    private void handleMouseMove(MouseEvent e, Tooltip tooltip) {
        if (isContiguous) {
            handleContiguousHover(e, tooltip);
        } else if (memory instanceof PagedMemoryManager) {
            handlePagingHover(e, tooltip);
        }
    }

    private void handleContiguousHover(MouseEvent e, Tooltip tooltip) {
        if (!isContiguous)
            return;

        ContiguousMemoryManager cmm = (ContiguousMemoryManager) memory;
        double w = getWidth();
        int totalMem = 12 * 1024 * 1024; // 12MB
        double scale = w / totalMem;

        double x = e.getX();
        double y = e.getY();

        // Only trigger within the bar area
        if (y < 50 || y > 110) {
            tooltip.hide();
            return;
        }

        // Convert X to Address
        int address = (int) (x / scale);

        // Search Allocations
        for (ProcessBlock pb : cmm.getAllocatedBlocks()) {
            if (address >= pb.start && address < pb.start + pb.size) {
                tooltip.setText(String.format("PID: %d\nStart: 0x%08X\nSize: %d bytes",
                        pb.pid, pb.start, pb.size));
                tooltip.show(canvas, e.getScreenX() + 10, e.getScreenY() + 10);
                return;
            }
        }

        // Search Free Blocks
        for (MemoryBlock mb : cmm.getFreeBlocks()) {
            if (address >= mb.start && address < mb.start + mb.size) {
                tooltip.setText(String.format("FREE\nStart: 0x%08X\nSize: %d bytes",
                        mb.start, mb.size));
                tooltip.show(canvas, e.getScreenX() + 10, e.getScreenY() + 10);
                return;
            }
        }

        tooltip.hide();
    }

    private void handlePagingHover(MouseEvent e, Tooltip tooltip) {
        PagedMemoryManager pmm = (PagedMemoryManager) memory;
        FrameOwner[] frames = pmm.getFrameOwners();

        double w = getWidth();
        double boxSize = 15;
        double gap = 1;
        double startY = 40;
        int cols = (int) (w / (boxSize + gap));
        if (cols < 1)
            cols = 1;

        double mx = e.getX();
        double my = e.getY();

        if (my < startY) {
            tooltip.hide();
            return;
        }

        // Calculate Frame Index
        int col = (int) (mx / (boxSize + gap));
        int row = (int) ((my - startY) / (boxSize + gap));
        int index = row * cols + col;

        if (index >= 0 && index < frames.length) {
            FrameOwner owner = frames[index];
            if (owner == null) {
                tooltip.setText(String.format("Frame %d: Free", index));
            } else if (owner.pid == -1) {
                tooltip.setText(String.format("Frame %d: Page Table", index));
            } else {
                tooltip.setText(String.format("Frame %d\nPID: %d\nVPN: 0x%X", index, owner.pid, owner.vpn));
            }
            tooltip.show(canvas, e.getScreenX() + 10, e.getScreenY() + 10);
        } else {
            tooltip.hide();
        }
    }
}
