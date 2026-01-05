package cse311.gui;

import cse311.RV32Computer;
import cse311.RV32Cpu;
import cse311.gui.components.ConsoleView;
import cse311.gui.components.MemoryView;
import cse311.gui.components.SchedulerView;
import cse311.kernel.Kernel;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import cse311.kernel.process.Task;

public class MainController {

    private final Kernel kernel;
    private final RV32Computer computer;
    private final BorderPane root;

    // Components
    private cse311.gui.components.CpuView[] cpuViews; // Array of views
    private cse311.gui.components.AssemblyView[] assemblyViews; // Array of assembly views
    private TabPane cpuTabs; // To update tab titles
    private SchedulerView schedulerView;
    private MemoryView memoryView;
    private ConsoleView consoleView; // NEW

    // Controls
    private Button btnPause;
    private Button btnResume;
    private Slider speedSlider;
    private Label lblStatus;

    public MainController(Kernel kernel, RV32Computer computer) {
        this.kernel = kernel;
        this.computer = computer;
        this.root = new BorderPane();
        initializeUI();
        startUpdateLoop();
    }

    private void initializeUI() {
        // --- TOP: Toolbar ---
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(10));
        toolbar.setStyle("-fx-background-color: #ddd; -fx-border-color: #bbb; -fx-border-width: 0 0 1 0;");

        btnPause = new Button("Pause");
        btnPause.setOnAction(e -> kernel.pause());

        btnResume = new Button("Resume");
        btnResume.setOnAction(e -> kernel.resume());

        Label lblSpeed = new Label("Delay (ms):");
        speedSlider = new Slider(0, 500, 0); // 0 to 500ms delay
        speedSlider.setShowTickLabels(true);
        speedSlider.setShowTickMarks(true);
        speedSlider.setMajorTickUnit(100);
        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            kernel.setExecutionSpeed(newVal.intValue());
        });

        lblStatus = new Label("Status: Running");

        toolbar.getChildren().addAll(btnPause, btnResume, new Separator(), lblSpeed, speedSlider, new Separator(),
                lblStatus);
        root.setTop(toolbar);

        // --- CENTER: Views ---
        SplitPane splitPane = new SplitPane();

        // LEFT: CPU & Scheduler
        SplitPane leftSplit = new SplitPane();
        leftSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);

        // Top Half: CPU
        VBox cpuBox = new VBox(10);
        cpuBox.setPadding(new Insets(10));

        // Bottom Half: Scheduler
        VBox schedulerBox = new VBox(10);
        schedulerBox.setPadding(new Insets(10));

        // Multi-Core Support: Use TabPane
        cpuTabs = new TabPane();
        int coreCount = kernel.getConfig().getCoreCount();
        cpuViews = new cse311.gui.components.CpuView[coreCount];
        assemblyViews = new cse311.gui.components.AssemblyView[coreCount];

        for (int i = 0; i < coreCount; i++) {
            RV32Cpu cpu = kernel.getCpu(i);

            // View 1: CPU Registers
            cpuViews[i] = new cse311.gui.components.CpuView(cpu);

            // View 2: Assembly Code
            assemblyViews[i] = new cse311.gui.components.AssemblyView(cpu, kernel.getMemory());

            // Layout for this Hart's Tab
            VBox hartLayout = new VBox(10, cpuViews[i], new Separator(), new Label("Instruction Stream"),
                    assemblyViews[i]);
            hartLayout.setPadding(new Insets(5));
            VBox.setVgrow(assemblyViews[i], Priority.ALWAYS); // Assembly view takes remaining space

            Tab tab = new Tab("Hart " + i, hartLayout);
            tab.setClosable(false);
            cpuTabs.getTabs().add(tab);
        }

        schedulerView = new SchedulerView(kernel);

        // Add to respective boxes
        cpuBox.getChildren().addAll(new Label("CPU State (Harts)"), cpuTabs);
        VBox.setVgrow(cpuTabs, Priority.ALWAYS);

        schedulerBox.getChildren().addAll(new Label("Scheduler Queues"), schedulerView);
        VBox.setVgrow(schedulerView, Priority.ALWAYS);

        leftSplit.getItems().addAll(cpuBox, schedulerBox);
        leftSplit.setDividerPositions(0.6); // Give 60% to CPU by default

        // RIGHT: Memory
        VBox rightPane = new VBox(10);
        rightPane.setPadding(new Insets(10));

        memoryView = new MemoryView(kernel.getMemory(), kernel.getMemoryCoordinator());

        rightPane.getChildren().addAll(new Label("Memory Visualization"), memoryView);
        VBox.setVgrow(memoryView, Priority.ALWAYS);

        // BOTTOM: Console (NEW)
        consoleView = new ConsoleView(kernel.getMemory());
        consoleView.setPrefHeight(200);

        // Add to layout
        splitPane.getItems().addAll(leftSplit, rightPane);
        splitPane.setDividerPositions(0.4);

        BorderPane centerLayout = new BorderPane();
        centerLayout.setCenter(splitPane);
        centerLayout.setBottom(consoleView);

        root.setCenter(centerLayout);

        // Redirect System.out and System.err to ConsoleView
        try {
            cse311.gui.util.GuiOutputStream guiOut = new cse311.gui.util.GuiOutputStream(consoleView.getOutputArea());
            java.io.PrintStream printStream = new java.io.PrintStream(guiOut, true);
            System.setOut(printStream);
            System.setErr(printStream);
            System.out.println("GUI: Console Output Redirected.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startUpdateLoop() {
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                updateUI();
            }
        };
        timer.start();
    }

    private void updateUI() {
        // Update Status Label
        if (kernel.isPaused()) {
            lblStatus.setText("Status: PAUSED");
            lblStatus.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        } else {
            lblStatus.setText("Status: RUNNING");
            lblStatus.setStyle("-fx-text-fill: green;");
        }

        // Refresh Sub-views
        for (int i = 0; i < cpuViews.length; i++) {
            cpuViews[i].update();
            assemblyViews[i].update();

            // Update Tab Title with PID
            Task task = kernel.getCpu(i).getCurrentTask();
            int pid = (task != null) ? task.getId() : 0;
            cpuTabs.getTabs().get(i).setText("Hart " + i + " [PID: " + pid + "]");
        }
        schedulerView.update();
        memoryView.update();
    }

    public Parent getView() {
        return root;
    }
}
