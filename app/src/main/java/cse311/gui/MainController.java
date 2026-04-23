package cse311.gui;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.net.URL;
import java.util.ResourceBundle;

import cse311.kernel.Kernel;
import cse311.RV32Computer;
import cse311.RV32Cpu;
import cse311.kernel.process.Task;
import cse311.gui.components.*;

public class MainController implements Initializable {

    // Inject items from FXML using their fx:id
    @FXML
    private Button btnPause;
    @FXML
    private Button btnResume;
    @FXML
    private Slider speedSlider;
    @FXML
    private Label lblStatus;

    // Containers where we will add our dynamic Java components
    @FXML
    private VBox sidebarContainer;
    @FXML
    private TabPane cpuTabs;
    @FXML
    private VBox schedulerContainer;
    @FXML
    private VBox memoryContainer; // Dashboard memory view
    @FXML
    private VBox memoryTabContainer; // Full memory tab view

    // Datapath components

    @FXML
    private VBox consoleContainer;

    @FXML
    private StackPane viewStack;
    @FXML
    private SplitPane dashboardPane;

    private final Kernel kernel;
    private final RV32Computer computer;

    // Sub-components (Logic remains in Java)
    private CpuView[] cpuViews;
    private AssemblyView[] assemblyViews;
    private SchedulerView schedulerView;
    private MemoryView memoryView;
    private HexMemoryView hexMemoryView;

    private SidebarView sidebarView;
    private ConsoleView consoleView;

    private ProcessorHandler processorHandler;

    // Placeholders for dynamic AssemblyView switching (Option B)
    private VBox[] dashboardAssemblyContainers;
    private VBox[] datapathAssemblyContainers;

    public MainController(Kernel kernel, RV32Computer computer) {
        this.kernel = kernel;
        this.computer = computer;
        // NOTE: We don't build UI here anymore!
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeToolbarLogic();
        initializeDashboard();
        initializeDatapath();
        initializeConsole();
        initializeSidebar();

        processorHandler = new ProcessorHandler(kernel);
        processorHandler.bindCpuSignals();
        processorHandler.onUiUpdate(this::updateUI);
    }

    private void initializeToolbarLogic() {
        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            kernel.setExecutionSpeed(newVal.intValue());
        });

        // Initial status update
        lblStatus.setText("Status: Running");
    }

    @FXML
    public void handlePause() {
        kernel.pause();
    }

    @FXML
    public void handleResume() {
        kernel.resume();
    }

    private void initializeDashboard() {
        // Logic to create CpuViews and add them to the FXML-injected 'cpuTabs'
        int coreCount = kernel.getConfig().getCoreCount();
        cpuViews = new CpuView[coreCount];
        assemblyViews = new AssemblyView[coreCount];
        dashboardAssemblyContainers = new VBox[coreCount];

        for (int i = 0; i < coreCount; i++) {
            var cpu = kernel.getCpu(i);
            cpuViews[i] = new CpuView(cpu);
            assemblyViews[i] = new AssemblyView(cpu, kernel.getMemory());

            // Create placeholder container for this core's AssemblyView in Dashboard
            dashboardAssemblyContainers[i] = new VBox();
            VBox.setVgrow(dashboardAssemblyContainers[i], Priority.ALWAYS);

            // Initialize with AssemblyView in Dashboard by default
            dashboardAssemblyContainers[i].getChildren().setAll(assemblyViews[i]);

            // 1. Wrap the CPU Registers in a ScrollPane so they don't force a massive
            // height
            ScrollPane cpuScroll = new ScrollPane(cpuViews[i]);
            cpuScroll.setFitToWidth(true); // Keeps your responsive grid wrapping active

            // 2. Use a vertical SplitPane instead of a standard VBox
            SplitPane hartSplit = new SplitPane();
            hartSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);

            // Top half: CPU Registers
            VBox topHalf = new VBox(5, cpuScroll);
            VBox.setVgrow(cpuScroll, Priority.ALWAYS);

            // Bottom half: Assembly View (initialized in Dashboard by default)
            VBox bottomHalf = new VBox(5, new Label("Instruction Stream"), dashboardAssemblyContainers[i]);
            VBox.setVgrow(dashboardAssemblyContainers[i], Priority.ALWAYS);

            // Add both halves to the SplitPane and set default sizes (e.g., 40% top, 60%
            // bottom)
            hartSplit.getItems().addAll(topHalf, bottomHalf);
            hartSplit.setDividerPositions(0.4);

            Tab tab = new Tab("Hart " + i, hartSplit);
            tab.setClosable(false);
            cpuTabs.getTabs().add(tab);
        }

        // Add Scheduler
        schedulerView = new SchedulerView(kernel);
        schedulerContainer.getChildren().add(schedulerView);
        VBox.setVgrow(schedulerView, Priority.ALWAYS);

        // Add Memory (Dashboard)
        memoryView = new MemoryView(kernel.getMemory(), kernel.getMemoryCoordinator());
        memoryContainer.getChildren().add(memoryView);
        VBox.setVgrow(memoryView, Priority.ALWAYS);

        // Add Memory (Full Tab)
        hexMemoryView = new HexMemoryView(kernel.getMemory());
        memoryTabContainer.getChildren().add(hexMemoryView);
        VBox.setVgrow(hexMemoryView, Priority.ALWAYS);
    }

    @FXML
    private TabPane datapathTabs;

    private DatapathView[] datapathViews;
    // We'll reuse the dashboard assemblyViews instead of creating separate
    // instances
    // This allows dynamic switching via Option B

    private void initializeDatapath() {
        int coreCount = kernel.getConfig().getCoreCount();
        datapathViews = new DatapathView[coreCount];
        datapathAssemblyContainers = new VBox[coreCount];

        for (int i = 0; i < coreCount; i++) {
            var cpu = kernel.getCpu(i);
            datapathViews[i] = new DatapathView(cpu);

            // Create placeholder container for this core's AssemblyView in Datapath
            datapathAssemblyContainers[i] = new VBox();
            VBox.setVgrow(datapathAssemblyContainers[i], Priority.ALWAYS);

            // 1. Wrap the large Datapath diagram in a ScrollPane
            ScrollPane datapathScroll = new ScrollPane(datapathViews[i]);
            datapathScroll.setPannable(true); // Lets you click and drag to pan around the diagram
            datapathScroll.setFitToHeight(true);
            datapathScroll.setFitToWidth(true);

            // 2. Create the SplitPane with HORIZONTAL orientation (side-by-side)
            SplitPane datapathSplit = new SplitPane();
            datapathSplit.setOrientation(javafx.geometry.Orientation.HORIZONTAL);

            // 3. Add the scrollable diagram and assembly placeholder
            datapathSplit.getItems().addAll(datapathScroll, datapathAssemblyContainers[i]);

            // 4. Give the diagram 70% of the space and the assembly view 30%
            datapathSplit.setDividerPositions(0.7);

            Tab tab = new Tab("Hart " + i, datapathSplit);
            tab.setClosable(false);

            datapathTabs.getTabs().add(tab);
        }
    }

    private void initializeConsole() {
        consoleView = new ConsoleView(kernel.getMemory());
        consoleView.setPrefHeight(200);
        consoleContainer.getChildren().add(consoleView);

        // Pass ConsoleView reference to Kernel for deadlock warnings
        kernel.setConsoleView(consoleView);

        // Redirect System.out and System.err to ConsoleView
        try {
            cse311.gui.util.GuiOutputStream guiOut = new cse311.gui.util.GuiOutputStream(consoleView.getOutputArea());
            java.io.PrintStream printStream = new java.io.PrintStream(guiOut, true);
            System.setOut(printStream);
            System.setErr(printStream);
            cse311.Logger.FileLogger.log(cse311.Logger.FileLogger.LogLevel.INFO, "GUI: Console Output Redirected.");
        } catch (Exception e) {
            cse311.Logger.FileLogger.log(e);
        }
    }

    private void initializeSidebar() {
        sidebarView = new SidebarView(view -> {
            int coreCount = kernel.getConfig().getCoreCount();

            // Teleport AssemblyViews to the correct location based on navigation
            if (view.equals("dashboard")) {
                // 1. Teleport Assembly Views to the Dashboard
                for (int i = 0; i < coreCount; i++) {
                    if (assemblyViews[i] != null) {
                        // Clear from datapath container
                        if (datapathAssemblyContainers[i] != null) {
                            datapathAssemblyContainers[i].getChildren().clear();
                        }
                        // Add to dashboard container
                        if (dashboardAssemblyContainers[i] != null) {
                            dashboardAssemblyContainers[i].getChildren().setAll(assemblyViews[i]);
                        }
                    }
                }

                // 2. Show Dashboard
                hideAllViews();
                dashboardPane.setVisible(true);
                dashboardPane.toFront();

            } else if (view.equals("datapath")) {
                // 1. Teleport Assembly Views to the Datapath
                for (int i = 0; i < coreCount; i++) {
                    if (assemblyViews[i] != null) {
                        // Clear from dashboard container
                        if (dashboardAssemblyContainers[i] != null) {
                            dashboardAssemblyContainers[i].getChildren().clear();
                        }
                        // Add to datapath container
                        if (datapathAssemblyContainers[i] != null) {
                            datapathAssemblyContainers[i].getChildren().setAll(assemblyViews[i]);
                        }
                    }
                }

                // 2. Show Datapath
                hideAllViews();
                datapathTabs.setVisible(true);
                datapathTabs.toFront();

            } else if (view.equals("memory")) {
                // Memory view doesn't need AssemblyView, just hide everything
                hideAllViews();
                memoryTabContainer.setVisible(true);
                memoryTabContainer.toFront();
            }
        });
        sidebarContainer.getChildren().add(sidebarView);
    }

    private void hideAllViews() {
        dashboardPane.setVisible(false);
        datapathTabs.setVisible(false);
        memoryTabContainer.setVisible(false);
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

        // Update components based on visibility to save resources
        if (dashboardPane.isVisible()) {
            // Refresh Dashboard sub-views
            for (int i = 0; i < cpuViews.length; i++) {
                cpuViews[i].update();
                // AssemblyView is updated via the container it's in
                if (dashboardAssemblyContainers[i] != null &&
                        !dashboardAssemblyContainers[i].getChildren().isEmpty()) {
                    assemblyViews[i].update();
                }

                // Update Tab Title with PID
                Task task = kernel.getCpu(i).getCurrentTask();
                int pid = (task != null) ? task.getId() : 0;
                cpuTabs.getTabs().get(i).setText("Hart " + i + " [PID: " + pid + "]");
            }
            schedulerView.update();
            memoryView.update();
        }

        if (datapathTabs.isVisible()) {
            for (int i = 0; i < datapathViews.length; i++) {
                datapathViews[i].update();
                // Update AssemblyView if it's in the datapath container
                if (datapathAssemblyContainers[i] != null &&
                        !datapathAssemblyContainers[i].getChildren().isEmpty()) {
                    assemblyViews[i].update();
                }
            }
        }

        if (memoryTabContainer.isVisible()) {
            hexMemoryView.update();
        }
    }
}
