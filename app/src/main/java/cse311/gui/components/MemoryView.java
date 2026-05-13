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
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
public class MemoryView extends Pane {

    private final MemoryManager memory;
    private final Canvas canvas;
    private final boolean isContiguous;

    // Color palette for processes
    private static final Color[] PROCESS_COLORS = {
            Color.SALMON, Color.LIGHTBLUE, Color.ORANGE, Color.VIOLET,
            Color.CYAN, Color.GOLD, Color.PINK
    };

    // Fragmentation gauge colors
    private static final Color FRAG_GREEN = Color.web("#27AE60");
    private static final Color FRAG_AMBER = Color.web("#F39C12");
    private static final Color FRAG_RED = Color.web("#C0392B");
    private static final Color FRAG_NEEDLE_GREEN = Color.web("#1E8449");
    private static final Color FRAG_NEEDLE_AMBER = Color.web("#E67E22");

    // Locality badge colors
    private static final Color LOCALITY_GOOD = Color.web("#1E8449");
    private static final Color LOCALITY_MODERATE = Color.web("#B7770D");
    private static final Color LOCALITY_POOR = Color.web("#C0392B");

    // Fonts
    private static final String FONT_FAMILY = "Segoe UI";
    private static final Font METRIC_FONT = Font.font(FONT_FAMILY, 13);
    private static final Font GAUGE_VALUE_FONT = Font.font(FONT_FAMILY, FontWeight.BOLD, 13);
    private static final Font GAUGE_WARN_FONT = Font.font(FONT_FAMILY, 11);
    private static final Font STRIP_FONT = Font.font(FONT_FAMILY, 11);

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

    // ========================= CONTIGUOUS VIEW =========================

    private void drawContiguous(GraphicsContext gc, double w, double h) {
        ContiguousMemoryManager cmm = (ContiguousMemoryManager) memory;

        int totalMem = 12 * 1024 * 1024; // 12MB
        double scale = w / totalMem;
        double barHeight = 60;
        double y = 50;

        // --- Compute fragmentation metrics ---
        List<MemoryBlock> freeList = cmm.getFreeBlocks();
        long totalFreeBytes = freeList.stream().mapToLong(b -> b.size).sum();
        long maxBlockBytes = freeList.stream().mapToLong(b -> b.size).max().orElse(0L);
        double fragIndex = totalFreeBytes > 0
                ? (1.0 - (double) maxBlockBytes / totalFreeBytes) * 100.0
                : 0.0;

        // --- Draw metric labels ---
        String totalFreeStr = String.format("Free: %.1f MB", totalFreeBytes / 1048576.0);
        String maxBlockStr = String.format("Max block: %.1f MB", maxBlockBytes / 1048576.0);
        String fragStr = String.format("Fragmentation: %.0f%%", fragIndex);

        gc.setFont(METRIC_FONT);
        gc.setFill(Color.BLACK);
        gc.fillText(totalFreeStr, 10, 18);
        gc.fillText(maxBlockStr, 10 + w / 3, 18);

        // Color the fragmentation label by severity
        gc.setFill(fragIndex >= 70 ? FRAG_RED
                : fragIndex >= 40 ? FRAG_AMBER
                        : FRAG_GREEN);
        gc.fillText(fragStr, 10 + 2 * w / 3, 18);

        // --- Draw tape bar ---
        gc.setFill(Color.LIGHTGRAY);
        gc.fillRect(0, y, w, barHeight);
        gc.setStroke(Color.BLACK);
        gc.strokeRect(0, y, w, barHeight);

        // Free blocks in green
        gc.setFill(Color.LIGHTGREEN);
        for (MemoryBlock b : freeList) {
            double x = b.start * scale;
            double bw = b.size * scale;
            gc.fillRect(x, y, bw, barHeight);
            gc.strokeRect(x, y, bw, barHeight);
        }

        // Allocated blocks with process colors
        List<ProcessBlock> allocatedList = cmm.getAllocatedBlocks();
        for (ProcessBlock pb : allocatedList) {
            double x = pb.start * scale;
            double bw = pb.size * scale;

            gc.setFill(PROCESS_COLORS[pb.pid % PROCESS_COLORS.length]);
            gc.fillRect(x, y, bw, barHeight);

            if (bw > 20) {
                gc.setFill(Color.BLACK);
                gc.fillText("P" + pb.pid, x + 2, y + barHeight / 2 + 5);
            }
        }

        // --- Draw fragmentation gauge ---
        drawFragGauge(gc, w / 2, h - 60, Math.min(w, 200) / 2 - 10, fragIndex);
    }

    private void drawFragGauge(GraphicsContext gc, double cx, double cy,
            double r, double fragPct) {
        gc.setLineWidth(14);
        gc.setLineCap(StrokeLineCap.BUTT);

        // Zone arcs: green 0-40%, amber 40-70%, red 70-100%
        drawArc(gc, cx, cy, r, 180, 72, FRAG_GREEN); // 40% of 180°
        drawArc(gc, cx, cy, r, 252, 54, FRAG_AMBER); // 30% of 180°
        drawArc(gc, cx, cy, r, 306, 54, FRAG_RED); // 30% of 180°

        // Needle
        double angle = Math.toRadians(180 + (fragPct / 100.0) * 180);
        double nx = cx + (r - 8) * Math.cos(angle);
        double ny = cy + (r - 8) * Math.sin(angle);

        Color nColor = fragPct >= 70 ? FRAG_RED
                : fragPct >= 40 ? FRAG_NEEDLE_AMBER
                        : FRAG_NEEDLE_GREEN;

        gc.setStroke(nColor);
        gc.setLineWidth(3);
        gc.strokeLine(cx, cy, nx, ny);

        gc.setFill(nColor);
        gc.fillOval(cx - 5, cy - 5, 10, 10);

        // Value label
        gc.setFill(Color.BLACK);
        gc.setFont(GAUGE_VALUE_FONT);
        gc.fillText(String.format("%.0f%%", fragPct), cx - 14, cy - 14);

        // Red zone warning
        if (fragPct >= 70) {
            gc.setFill(FRAG_RED);
            gc.setFont(GAUGE_WARN_FONT);
            gc.fillText("OOM risk", cx - 22, cy + 16);
        }
    }

    private void drawArc(GraphicsContext gc, double cx, double cy, double r,
            double startDeg, double sweepDeg, Color color) {
        gc.setStroke(color);
        gc.strokeArc(cx - r, cy - r, r * 2, r * 2,
                -startDeg, -sweepDeg,
                ArcType.OPEN);
    }

    // ========================= PAGING VIEW =========================

    private void drawPaging(GraphicsContext gc, double w, double h) {
        PagedMemoryManager pmm = (PagedMemoryManager) memory;
        FrameOwner[] frames = pmm.getFrameOwners();
        int totalFrames = pmm.getTotalFrames();
        int[] heat = pmm.getFrameHeat();

        // Calculate grid dimensions
        double boxSize = 15;
        double gap = 1;
        double startY = 40;

        int cols = (int) (w / (boxSize + gap));
        if (cols < 1)
            cols = 1;

        // Calculate grid bottom for locality strip positioning
        int totalRows = (totalFrames + cols - 1) / cols;
        double gridBottom = startY + totalRows * (boxSize + gap);

        for (int i = 0; i < totalFrames; i++) {
            int row = i / cols;
            int col = i % cols;

            double x = col * (boxSize + gap);
            double y = startY + row * (boxSize + gap);

            FrameOwner owner = frames[i];
            if (owner == null) {
                // Free frame - light gray
                gc.setFill(Color.LIGHTGRAY);
                gc.fillRect(x, y, boxSize, boxSize);
            } else if (owner.pid == -1) {
                // Page Table Frame - dark gray
                gc.setFill(Color.DARKGRAY);
                gc.fillRect(x, y, boxSize, boxSize);
                gc.setStroke(Color.BLACK);
                gc.setLineWidth(0.5);
                gc.strokeRect(x, y, boxSize, boxSize);
            } else {
                // User frame - apply heat brightness modulation
                Color frameColor = PROCESS_COLORS[owner.pid % PROCESS_COLORS.length];
                boolean hasData = !pmm.isFrameEmpty(i);

                if (hasData) {
                    // Modulate brightness by heat: 0.55 when cold, up to 1.1 when fully hot
                    double heatVal = (heat != null && i < heat.length) ? heat[i] / 255.0 : 0.0;
                    Color displayColor = frameColor.deriveColor(0, 1.0, 0.55 + heatVal * 0.55, 1.0);
                    gc.setFill(displayColor);
                    gc.fillRect(x, y, boxSize, boxSize);
                    gc.setStroke(Color.BLACK);
                    gc.setLineWidth(0.5);
                    gc.strokeRect(x, y, boxSize, boxSize);
                } else {
                    // Allocated but empty - hollow outline with hatching
                    gc.setFill(Color.WHITE);
                    gc.fillRect(x, y, boxSize, boxSize);
                    gc.setStroke(frameColor);
                    gc.setLineWidth(2);
                    gc.strokeRect(x + 1, y + 1, boxSize - 2, boxSize - 2);

                    gc.setStroke(frameColor.deriveColor(0, 1, 0.7, 0.5));
                    gc.setLineWidth(1);
                    gc.strokeLine(x + 2, y + boxSize - 2, x + boxSize - 2, y + 2);
                }
            }
        }

        gc.setFill(Color.BLACK);
        gc.fillText(
                "Paging Mode: " + totalFrames
                        + " Frames (4KB each)  |  Solid = Has Data, Hollow = Allocated but Empty  |  Brightness = Access Heat",
                10, 20);

        // Draw locality strips below the frame grid
        drawLocalityStrips(gc, w, h, frames, totalFrames, gridBottom);
    }

    private void drawLocalityStrips(GraphicsContext gc, double w, double h,
            FrameOwner[] frames, int totalFrames,
            double gridBottom) {
        // Collect frame indices per PID
        Map<Integer, List<Integer>> pidFrames = new LinkedHashMap<>();
        for (int i = 0; i < totalFrames; i++) {
            FrameOwner o = frames[i];
            if (o != null && o.pid >= 0) {
                pidFrames.computeIfAbsent(o.pid, k -> new ArrayList<>()).add(i);
            }
        }

        if (pidFrames.isEmpty())
            return;

        // Sort by frame count descending (most significant processes first)
        List<Map.Entry<Integer, List<Integer>>> sorted = new ArrayList<>(pidFrames.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));

        // Cap strips to available space below the frame grid
        double stripHeight = 18;
        double stripGap = 2;
        double headerHeight = 20;
        double availableHeight = h - gridBottom - headerHeight;
        int maxStrips = Math.max(1, (int) (availableHeight / (stripHeight + stripGap)));
        int displayCount = Math.min(sorted.size(), maxStrips);

        double stripY = gridBottom + headerHeight;

        gc.setFont(STRIP_FONT);
        gc.setFill(Color.BLACK);
        gc.fillText("Locality per process:", 6, stripY - 4);

        for (int idx = 0; idx < displayCount; idx++) {
            Map.Entry<Integer, List<Integer>> entry = sorted.get(idx);
            int pid = entry.getKey();
            List<Integer> owned = entry.getValue();

            int minF = owned.stream().mapToInt(x -> x).min().orElse(0);
            int maxF = owned.stream().mapToInt(x -> x).max().orElse(0);
            int span = maxF - minF + 1;
            double locality = owned.size() * 100.0 / span;

            // Strip bar
            double barX = 60;
            double barW = w - 160;
            Color pidColor = PROCESS_COLORS[pid % PROCESS_COLORS.length];

            // Background (full span)
            gc.setFill(Color.LIGHTGRAY.deriveColor(0, 1, 1, 0.4));
            gc.fillRect(barX + (minF / (double) totalFrames) * barW,
                    stripY, (span / (double) totalFrames) * barW, stripHeight);

            // Owned frames as individual ticks
            gc.setFill(pidColor);
            for (int fi : owned) {
                double fx = barX + (fi / (double) totalFrames) * barW;
                gc.fillRect(fx, stripY, Math.max(1, barW / totalFrames - 1), stripHeight);
            }

            // PID label
            gc.setFill(Color.BLACK);
            gc.fillText("P" + pid, 6, stripY + 12);

            // Locality badge
            String badge = locality >= 80 ? "Good" : locality >= 50 ? "Moderate" : "Poor";
            gc.setFill(locality >= 80 ? LOCALITY_GOOD
                    : locality >= 50 ? LOCALITY_MODERATE
                            : LOCALITY_POOR);
            gc.fillText(badge + String.format(" %.0f%%", locality),
                    barX + (minF / (double) totalFrames) * barW +
                            (span / (double) totalFrames) * barW + 4,
                    stripY + 12);

            stripY += stripHeight + stripGap;
        }

        // Show truncation indicator if there are more processes
        if (sorted.size() > displayCount) {
            gc.setFill(Color.GRAY);
            gc.fillText("... and " + (sorted.size() - displayCount) + " more processes",
                    6, stripY + 12);
        }
    }

    // ========================= TOOLTIP HANDLERS =========================

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

            int realAddress = index * PagedMemoryManager.PAGE_SIZE;

            if (owner == null) {
                tooltip.setText(String.format(
                        "Frame: %d\n" +
                                "Status: Free\n" +
                                "Real Address: 0x%08X",
                        index, realAddress));
            } else if (owner.pid == -1) {
                tooltip.setText(String.format(
                        "Frame: %d\n" +
                                "Status: Page Table\n" +
                                "Real Address: 0x%08X",
                        index, realAddress));
            } else {
                boolean hasData = !pmm.isFrameEmpty(index);
                String dataStatus = hasData ? "YES (non-zero)" : "NO (all zeros)";

                // Include heat info in tooltip
                int[] heat = pmm.getFrameHeat();
                int heatVal = (heat != null && index < heat.length) ? heat[index] : 0;

                tooltip.setText(String.format(
                        "Frame: %d\n" +
                                "Real Address: 0x%08X\n" +
                                "-----------------\n" +
                                "Mapped to PID: %d\n" +
                                "Virtual Page: 0x%X\n" +
                                "-----------------\n" +
                                "Has Data: %s\n" +
                                "Heat: %d/255",
                        index, realAddress, owner.pid, owner.vpn, dataStatus, heatVal));
            }
            tooltip.show(canvas, e.getScreenX() + 10, e.getScreenY() + 10);
        } else {
            tooltip.hide();
        }
    }
}
