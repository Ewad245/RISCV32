package cse311.gui;

import cse311.RV32Computer;
import cse311.Constants.MemoryMode;
import cse311.Constants.OSConstants;
import cse311.Logger.FileLogger;
import cse311.kernel.Kernel;
import cse311.kernel.KernelConfig;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

public class GuiApp extends Application {

    private static RV32Computer computer;
    private static Kernel kernel;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // 1. Initialize Simulation (Backend)
        initializeSimulation();

        // 2. Create the FXMLLoader
        javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/MainLayout.fxml"));

        // 3. Set the Controller Factory
        loader.setControllerFactory(param -> new MainController(kernel, computer));

        // 4. Load the root
        javafx.scene.Parent root = loader.load();

        // 5. Show
        Scene scene = new Scene(root, 1200, 800);

        // Add Global CSS
        scene.getStylesheets().add(
                getClass().getResource("/css/style.css").toExternalForm());

        // 3. Configure Stage
        primaryStage.setTitle("RISC-V OS Simulator");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            kernel.stop();
            Platform.exit();
            System.exit(0);
        });

        // 4. Start Kernel (in background)
        // Note: Kernel start() creates its own threads.
        // We pause it initially to let user hit "Start" or we can just let it run.
        // For now, we'll let it run but maybe paused by default if we implemented pause
        // correctly early on.
        // Or we can just start it.
        kernel.start();

        primaryStage.show();
    }

    private void initializeSimulation() throws URISyntaxException {
        String file_separator = System.getProperty("file.separator");
        // Same logic as App.java
        FileLogger.log("GUI: Initializing Simulation Hardware...");

        // Initialize 128MB RAM, CPU, Memory Management Techniques
        // We can make this configurable later via a "New Simulation" dialog
        computer = new RV32Computer(1024 * 1024 * 12, Integer.MAX_VALUE, MemoryMode.PAGING);

        kernel = computer.getKernel();

        // KERNEL CONFIGURATION
        kernel.getConfig().setSchedulerType(KernelConfig.SchedulerType.ROUND_ROBIN);
        kernel.getConfig().setTimeSlice(3);
        kernel.getScheduler().setTimeSlice(3);

        // MOUNT FILE SYSTEM
        String imgPath = ".." + file_separator + "fs.img";
        File diskImage = new File(imgPath);
        if (diskImage.exists()) {
            kernel.mountFileSystem(imgPath);
            FileLogger.log("GUI: Disk image '" + imgPath + "' mounted.");
        } else {
            FileLogger.log(FileLogger.LogLevel.INFO,
                    "GUI: fs.img not found at " + imgPath + ". File system calls will fail.");
        }

        // LAUNCH INIT PROCESS
        // Use user_programs folder inside resources
        String resourcePath = "user_programs/init";

        URL resourceUrl = getClass().getClassLoader().getResource(resourcePath);
        String elfPath = Paths.get(resourceUrl.toURI()).toString();
        File f = new File(elfPath);

        FileLogger.log(FileLogger.LogLevel.DEBUG,
                "GUI: Looking for Init ELF at: " + f.getAbsolutePath());

        if (f.exists()) {
            try {
                FileLogger.log(FileLogger.LogLevel.DEBUG,
                        "GUI: Found init.elf. Creating task...");
                kernel.createTask(elfPath);
                FileLogger.log("GUI: Init task created (PID 1).");
            } catch (Exception e) {
                FileLogger.log(FileLogger.LogLevel.ERROR,
                        "GUI: Failed to create Init task: " + e.getMessage());
                FileLogger.log(e);
            }
        } else {
            // Fallback to old path for backwards compatibility
            String oldPath = ".." + file_separator + "User_Program_ELF" + file_separator + "init.elf";
            File oldFile = new File(oldPath);
            if (oldFile.exists()) {
                try {
                    FileLogger.log("GUI: Using legacy ELF path: " + oldPath);
                    kernel.createTask(oldPath);
                    FileLogger.log("GUI: Init task created from legacy path.");
                } catch (Exception e) {
                    FileLogger.log(FileLogger.LogLevel.ERROR,
                            "GUI: Failed to create Init task: " + e.getMessage());
                }
            } else {
                FileLogger.log(FileLogger.LogLevel.ERROR,
                        "GUI: Error - init.elf NOT FOUND at " + f.getAbsolutePath());
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
