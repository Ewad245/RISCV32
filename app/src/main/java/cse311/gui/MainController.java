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
import javafx.animation.AnimationTimer;

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
    private VBox memoryContainer;

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

    private SidebarView sidebarView;
    private ConsoleView consoleView;

    public MainController(Kernel kernel, RV32Computer computer) {
        this.kernel = kernel;
        this.computer = computer;
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

    private void initializeDashboard() {
        // Logic to create CpuViews and add them to the FXML-injected 'cpuTabs'
        int coreCount = kernel.getConfig().getCoreCount();
        cpuViews = new CpuView[coreCount];
        assemblyViews = new AssemblyView[coreCount];

        for (int i = 0; i < coreCount; i++) {
            var cpu = kernel.getCpu(i);
            cpuViews[i] = new CpuView(cpu);
            assemblyViews[i] = new AssemblyView(cpu, kernel.getMemory());

            VBox hartLayout = new VBox(10, cpuViews[i], new Separator(), new Label("Instruction Stream"),
                    assemblyViews[i]);
            VBox.setVgrow(assemblyViews[i], Priority.ALWAYS);

            Tab tab = new Tab("Hart " + i, hartLayout);
            tab.setClosable(false);
            cpuTabs.getTabs().add(tab);
        }

        // Add Scheduler
        schedulerView = new SchedulerView(kernel);
        schedulerContainer.getChildren().add(schedulerView);
        VBox.setVgrow(schedulerView, Priority.ALWAYS);

        // Add Memory
        memoryView = new MemoryView(kernel.getMemory(), kernel.getMemoryCoordinator());
        memoryContainer.getChildren().add(memoryView);
        VBox.setVgrow(memoryView, Priority.ALWAYS);
    }

    @FXML
    private TabPane datapathTabs;

    private DatapathView[] datapathViews;
    private AssemblyView[] datapathAssemblyViews;

    private void initializeDatapath() {
        int coreCount = kernel.getConfig().getCoreCount();
        datapathViews = new DatapathView[coreCount];
        datapathAssemblyViews = new AssemblyView[coreCount];

        for (int i = 0; i < coreCount; i++) {
            var cpu = kernel.getCpu(i);
            datapathViews[i] = new DatapathView(cpu);
            datapathAssemblyViews[i] = new AssemblyView(cpu, kernel.getMemory());

            VBox datapathContainer = new VBox(10, new Label("CPU Datapath"), datapathViews[i]);
            VBox.setVgrow(datapathViews[i], Priority.ALWAYS);

            VBox assemblyContainer = new VBox(10, new Label("Assembly Stream"), datapathAssemblyViews[i]);
            VBox.setVgrow(datapathAssemblyViews[i], Priority.ALWAYS);

            SplitPane split = new SplitPane(datapathContainer, assemblyContainer);
            split.setDividerPositions(0.7);

            Tab tab = new Tab("Hart " + i, split);
            tab.setClosable(false);
            datapathTabs.getTabs().add(tab);
        }
    }

    private void initializeConsole() {
        consoleView = new ConsoleView(kernel.getMemory());
        consoleView.setPrefHeight(200);
        consoleContainer.getChildren().add(consoleView);

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

    private void initializeSidebar() {
        sidebarView = new SidebarView(view -> {
            if (view.equals("dashboard")) {
                dashboardPane.setVisible(true);
                datapathTabs.setVisible(false);
                dashboardPane.toFront();
            } else if (view.equals("datapath")) {
                dashboardPane.setVisible(false);
                datapathTabs.setVisible(true);
                datapathTabs.toFront();
            }
        });
        sidebarContainer.getChildren().add(sidebarView);
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

        // Update components based on visibility to save resources
        if (dashboardPane.isVisible()) {
            // Refresh Dashboard sub-views
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

        if (datapathTabs.isVisible()) {
            for (int i = 0; i < datapathViews.length; i++) {
                datapathViews[i].update();
                datapathAssemblyViews[i].update();
            }
        }
    }
}
