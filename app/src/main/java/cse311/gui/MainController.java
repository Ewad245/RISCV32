package cse311.gui;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import java.net.URL;
import java.util.ResourceBundle;

import cse311.kernel.Kernel;
import cse311.RV32Computer;
import cse311.Constants.MemoryMode;
import cse311.gui.components.AssemblyView;
import cse311.gui.components.ConsoleView;
import cse311.gui.components.CpuView;
import cse311.gui.components.DatapathView;
import cse311.gui.components.HexMemoryView;
import cse311.gui.components.MemoryView;
import cse311.gui.components.SchedulerView;
import cse311.gui.components.SidebarView;
import cse311.kernel.process.Task;
import cse311.kernel.plugin.PluginLoader;
import cse311.kernel.scheduler.Scheduler;
import cse311.kernel.contiguous.AllocationStrategy;
import cse311.kernel.contiguous.ContiguousMemoryManager;
import cse311.kernel.NonContiguous.paging.PagedMemoryManager;
import cse311.kernel.NonContiguous.paging.ReplacementPolicy;
import cse311.kernel.NonContiguous.paging.DemandPager;
import cse311.MemoryManager;
import javafx.animation.AnimationTimer;
import javafx.stage.FileChooser;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Priority;
import javafx.application.Platform;
import cse311.gui.platform.PlatformEnvironment;
import java.io.File;

@SuppressWarnings({
        "PMD.AvoidDuplicateLiterals",
        "PMD.AvoidCatchingGenericException",
        "PMD.RelianceOnDefaultCharset",
        "PMD.AvoidLiteralsInIfCondition"
})
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
    @FXML
    private VBox screenContainer; // Screen tab view

    // Datapath components

    @FXML
    private VBox consoleContainer;

    @FXML
    private StackPane viewStack;
    @FXML
    private SplitPane dashboardPane;

    private final Kernel kernel;
    @SuppressWarnings("PMD.UnusedPrivateField")
    private final RV32Computer computer;
    private final GuiApp guiApp;

    // Sub-components (Logic remains in Java)
    private CpuView[] cpuViews;
    private AssemblyView[] assemblyViews;
    private SchedulerView schedulerView;
    private MemoryView memoryView;
    private HexMemoryView hexMemoryView;
    private cse311.gui.components.ScreenView screenView;

    private SidebarView sidebarView;
    private ConsoleView consoleView;
    private AnimationTimer uiUpdateTimer;

    // Placeholders for dynamic AssemblyView switching (Option B)
    private VBox[] dashboardAssemblyContainers;
    private VBox[] datapathAssemblyContainers;

    public MainController(Kernel kernel, RV32Computer computer, GuiApp guiApp) {
        this.kernel = kernel;
        this.computer = computer;
        this.guiApp = guiApp;
        // NOTE: We don't build UI here anymore!
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // This method is called automatically after FXML is loaded.
        // Initialize your custom dynamic components here.

        initializeToolbarLogic();
        initializeDashboard();
        initializeDatapath();
        initializeConsole();
        initializeSidebar();

        startUpdateLoop(); // Your existing animation timer logic
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

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    @FXML
    private void onNewSimulationClicked() {
        PlatformEnvironment.getManager().showConfirmationAlert(
            "New Simulation",
            "Start a New Simulation?",
            "This will stop the current simulation and reset all memory, registers, and loaded processes. Are you sure you want to continue?",
            btnPause.getScene().getWindow(),
            () -> {
                cse311.Logger.FileLogger.log(cse311.Logger.FileLogger.LogLevel.INFO, "Starting new simulation from menu.");
                MemoryMode currentMode = (kernel.getMemory() instanceof ContiguousMemoryManager) ? 
                        MemoryMode.CONTIGUOUS : MemoryMode.PAGING;
                guiApp.restartInMode(currentMode);
            }
        );
    }

    @FXML
    public void onLoadDiskImageClicked() {
        PlatformEnvironment.getManager().chooseOpenFile(
            btnPause,
            "Select Disk Image",
            "Disk Image Files",
            "*.img",
            selectedFile -> {
                guiApp.setDiskImagePath(selectedFile.getAbsolutePath());
                PlatformEnvironment.getManager().showConfirmationAlert(
                    "Restart Required",
                    "Load Disk Image",
                    "The new disk image path has been set. The simulator must restart to mount the new image. Do you want to restart now?",
                    btnPause.getScene().getWindow(),
                    () -> {
                        MemoryMode currentMode = (kernel.getMemory() instanceof ContiguousMemoryManager) ? 
                                MemoryMode.CONTIGUOUS : MemoryMode.PAGING;
                        guiApp.restartInMode(currentMode);
                    }
                );
            }
        );
    }

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    @FXML
    private void onExitClicked() {
        shutdown();
        PlatformEnvironment.getManager().exit(btnPause.getScene().getWindow());
    }

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    @FXML
    private void onAboutClicked() {
        PlatformEnvironment.getManager().showInfoAlert("About", "RISC-V OS Simulator", "Designed for CSE311.\nSupports dynamic algorithm hot-swapping.", btnPause.getScene().getWindow());
    }

    @FXML
    public void onImportSchedulerClicked() {
        PlatformEnvironment.getManager().chooseOpenFile(
            btnPause, "Select Custom Scheduler JAR", "JAR Files", "*.jar", selectedFile -> {
                try {
                    Scheduler customScheduler = PluginLoader.loadCustomScheduler(selectedFile);
                    customScheduler.setTimeSlice(kernel.getConfig().getTimeSlice());
                    kernel.setScheduler(customScheduler);
                    
                    PlatformEnvironment.getManager().showInfoAlert("Success", "Scheduler Loaded", 
                        "Successfully loaded and activated: " + customScheduler.getClass().getSimpleName(), 
                        btnPause.getScene().getWindow());
                } catch (Exception e) {
                    if (cse311.Logger.FileLogger.isLoggable(cse311.Logger.FileLogger.LogLevel.ERROR))
                        cse311.Logger.FileLogger.log(cse311.Logger.FileLogger.LogLevel.ERROR,
                                "Failed to load scheduler plugin: " + e.getMessage());
                    cse311.Logger.FileLogger.log(e);
                    PlatformEnvironment.getManager().showErrorAlert("Error", "Failed to Load Scheduler", 
                        "Error: " + e.getMessage(), btnPause.getScene().getWindow());
                }
            });
    }

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    @FXML
    private void onImportMemoryAllocatorClicked() {
        MemoryManager currentMemory = kernel.getMemory();

        if (!(currentMemory instanceof ContiguousMemoryManager)) {
            PlatformEnvironment.getManager().showConfirmationAlert("Mode Mismatch", "Switch to Contiguous Memory Mode?",
                "Allocation strategies only apply to Contiguous memory mode.\nSwitching will restart the simulator and reset all running processes.",
                btnPause.getScene().getWindow(), () -> {
                    guiApp.restartInMode(MemoryMode.CONTIGUOUS);
                });
            return;
        }

        PlatformEnvironment.getManager().chooseOpenFile(btnPause, "Select Custom Memory Allocator JAR", "JAR Files", "*.jar", selectedFile -> {
            try {
                AllocationStrategy customAllocator = PluginLoader.loadCustomAllocator(selectedFile);
                boolean wasRunning = !kernel.isPaused();
                kernel.pause();
                ((ContiguousMemoryManager) currentMemory).setAllocationStrategy(customAllocator);
                if (wasRunning) kernel.resume();
                PlatformEnvironment.getManager().showInfoAlert("Success", "Allocator Loaded", 
                    "Successfully hot-swapped memory allocator to: " + customAllocator.getClass().getSimpleName(), 
                    btnPause.getScene().getWindow());
            } catch (Exception e) {
                if (cse311.Logger.FileLogger.isLoggable(cse311.Logger.FileLogger.LogLevel.ERROR))
                    cse311.Logger.FileLogger.log(cse311.Logger.FileLogger.LogLevel.ERROR,
                            "Failed to load allocator plugin: " + e.getMessage());
                cse311.Logger.FileLogger.log(e);
                PlatformEnvironment.getManager().showErrorAlert("Import Error", "Failed to load plugin", 
                    e.getMessage(), btnPause.getScene().getWindow());
            }
        });
    }

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    @FXML
    private void onImportPageReplacementClicked() {
        MemoryManager currentMemory = kernel.getMemory();

        if (!(currentMemory instanceof PagedMemoryManager)) {
            PlatformEnvironment.getManager().showConfirmationAlert("Mode Mismatch", "Switch to Paging Memory Mode?",
                "Page replacement policies only apply to Paging memory mode.\nSwitching will restart the simulator and reset all running processes.",
                btnPause.getScene().getWindow(), () -> {
                    guiApp.restartInMode(MemoryMode.PAGING);
                });
            return;
        }

        PagedMemoryManager pmm = (PagedMemoryManager) currentMemory;

        PlatformEnvironment.getManager().chooseOpenFile(btnPause, "Select Custom Page Replacement JAR", "JAR Files", "*.jar", selectedFile -> {
            try {
                ReplacementPolicy customPolicy = PluginLoader.loadCustomReplacementPolicy(selectedFile);
                DemandPager newPager = new DemandPager(pmm, customPolicy);
                boolean wasRunning = !kernel.isPaused();
                kernel.pause();
                pmm.setPager(newPager);
                if (wasRunning) kernel.resume();
                PlatformEnvironment.getManager().showInfoAlert("Success", "Policy Loaded", 
                    "Successfully hot-swapped page replacement to: " + customPolicy.getClass().getSimpleName(), 
                    btnPause.getScene().getWindow());
            } catch (Exception e) {
                if (cse311.Logger.FileLogger.isLoggable(cse311.Logger.FileLogger.LogLevel.ERROR))
                    cse311.Logger.FileLogger.log(cse311.Logger.FileLogger.LogLevel.ERROR,
                            "Failed to load page replacement plugin: " + e.getMessage());
                cse311.Logger.FileLogger.log(e);
                PlatformEnvironment.getManager().showErrorAlert("Import Error", "Failed to load plugin", 
                    e.getMessage(), btnPause.getScene().getWindow());
            }
        });
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

    @SuppressWarnings("PMD.CloseResource")
    private void initializeConsole() {
        consoleView = new ConsoleView(kernel.getMemory());
        javafx.scene.layout.VBox.setVgrow(consoleView, javafx.scene.layout.Priority.ALWAYS);
        consoleContainer.getChildren().add(consoleView);

        // Pass ConsoleView reference to Kernel for deadlock warnings
        kernel.setConsoleView(consoleView);

        // Redirect System.out and System.err to ConsoleView
        try {
            java.io.PipedOutputStream pipedOut = new java.io.PipedOutputStream();
            java.io.PipedInputStream pipedIn = new java.io.PipedInputStream(pipedOut);

            // Create the connector that links the OS emulator to the terminal UI
            cse311.gui.components.EmulatorTtyConnector connector = 
                new cse311.gui.components.EmulatorTtyConnector(pipedIn, kernel.getMemory());
            consoleView.setTtyConnector(connector);

            // Filter to convert \n to \r\n for JediTermFX
            java.io.OutputStream crlfFilter = new java.io.OutputStream() {
                private int lastByte = -1;
                @Override
                public void write(int b) throws java.io.IOException {
                    if (b == '\n' && lastByte != '\r') {
                        pipedOut.write('\r');
                    }
                    pipedOut.write(b);
                    lastByte = b;
                }

                @Override
                public void write(byte[] b, int off, int len) throws java.io.IOException {
                    int extra = 0;
                    for (int i = 0; i < len; i++) {
                        int current = b[off + i];
                        int prev = (i == 0) ? lastByte : b[off + i - 1];
                        if (current == '\n' && prev != '\r') {
                            extra++;
                        }
                    }

                    if (extra == 0) {
                        pipedOut.write(b, off, len);
                        if (len > 0) {
                            lastByte = b[off + len - 1];
                        }
                        return;
                    }

                    byte[] newB = new byte[len + extra];
                    int j = 0;
                    for (int i = 0; i < len; i++) {
                        int current = b[off + i];
                        int prev = (i == 0) ? lastByte : b[off + i - 1];
                        if (current == '\n' && prev != '\r') {
                            newB[j] = '\r';
                            j++;
                        }
                        newB[j] = (byte) current;
                        j++;
                    }
                    pipedOut.write(newB, 0, newB.length);
                    if (len > 0) {
                        lastByte = b[off + len - 1];
                    }
                }

                @Override
                public void flush() throws java.io.IOException {
                    pipedOut.flush();
                }
            };

            java.io.PrintStream printStream = new java.io.PrintStream(crlfFilter, true);
            System.setOut(printStream);
            System.setErr(printStream);
            cse311.Logger.FileLogger.log(cse311.Logger.FileLogger.LogLevel.INFO, "GUI: Console Output Redirected to JediTermFX.");
        } catch (Exception e) {
            cse311.Logger.FileLogger.log(e);
        }
    }

    private void initializeSidebar() {
        sidebarView = new SidebarView(view -> {
            int coreCount = kernel.getConfig().getCoreCount();

            // Teleport AssemblyViews to the correct location based on navigation
            if ("dashboard".equals(view)) {
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

            } else if ("datapath".equals(view)) {
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

            } else if ("memory".equals(view)) {
                // Memory view doesn't need AssemblyView, just hide everything
                hideAllViews();
                memoryTabContainer.setVisible(true);
                memoryTabContainer.toFront();
            } else if ("screen".equals(view)) {
                hideAllViews();
                if (screenView == null && screenContainer != null) {
                    screenView = new cse311.gui.components.ScreenView(kernel.getMemory().getFramebufferDevice());
                    screenContainer.getChildren().add(screenView);
                    VBox.setVgrow(screenView, Priority.ALWAYS);
                    screenContainer.setMinSize(0, 0);
                    screenContainer.setMaxWidth(Double.MAX_VALUE);
                    screenContainer.setMaxHeight(Double.MAX_VALUE);
                }
                if (screenContainer != null) {
                    screenContainer.setVisible(true);
                    screenContainer.toFront();
                    if (screenView != null) {
                        screenView.requestFocus();
                    }
                }
            }
        });
        sidebarContainer.getChildren().add(sidebarView);
    }

    private void hideAllViews() {
        dashboardPane.setVisible(false);
        datapathTabs.setVisible(false);
        memoryTabContainer.setVisible(false);
        if (screenContainer != null) {
            screenContainer.setVisible(false);
        }
    }

    private void startUpdateLoop() {
        uiUpdateTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                updateUI();
            }
        };
        uiUpdateTimer.start();
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

    public void shutdown() {
        if (uiUpdateTimer != null) {
            uiUpdateTimer.stop();
        }
        if (screenView != null) {
            screenView.stop();
        }
        if (consoleView != null) {
            consoleView.close();
        }
        if (kernel != null) {
            kernel.stop();
        }
    }
}
