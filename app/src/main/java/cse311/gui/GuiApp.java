package cse311.gui;

import cse311.Constants.MemoryMode;
import cse311.Logger.FileLogger;
import cse311.RV32Computer;
import cse311.kernel.Kernel;
import cse311.kernel.KernelConfig;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import cse311.gui.platform.PlatformEnvironment;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class GuiApp extends Application {

    private RV32Computer computer;
    private Kernel kernel;
    private Stage primaryStage;
    private MemoryMode currentMode = MemoryMode.PAGING;
    private String customDiskImagePath = null;
    private MainController currentController;
    private String tempDiskImagePath = null;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        rebuildScene(currentMode);
        primaryStage.show();
    }

    /**
     * Rebuilds the application in the requested memory mode.
     * This stops the old kernel, creates fresh hardware/kernel state,
     * and reloads the UI with the new kernel.
     */
    public void setDiskImagePath(String path) {
        customDiskImagePath = path;
    }

    public void restartInMode(MemoryMode mode) {
        currentMode = mode;
        if (kernel != null) {
            kernel.stop();
        }
        if (currentController != null) {
            currentController.shutdown();
        }
        try {
            rebuildScene(mode);
        } catch (Exception e) {
            FileLogger.log(FileLogger.LogLevel.ERROR,
                    "GUI: Failed to restart in " + mode + " mode: " + e.getMessage());
            FileLogger.log(e);
        }
    }

    private void rebuildScene(MemoryMode mode) throws Exception {
        initializeSimulation(mode);

        javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                GuiApp.class.getResource("/fxml/MainLayout.fxml"));
        loader.setControllerFactory(param -> new MainController(kernel, computer, this));

        javafx.scene.Parent root = loader.load();
        currentController = loader.getController();

        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(
                GuiApp.class.getResource("/css/style.css").toExternalForm());

        primaryStage.setTitle("RISC-V OS Simulator");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            if (kernel != null) {
                kernel.stop();
            }
            if (currentController != null) {
                currentController.shutdown();
            }
            PlatformEnvironment.getManager().exit(primaryStage);
        });
        
        PlatformEnvironment.getManager().registerSessionCleanup(primaryStage, () -> {
            if (kernel != null) {
                kernel.stop();
            }
            if (currentController != null) {
                currentController.shutdown();
            }
            if (tempDiskImagePath != null) {
                try {
                    java.nio.file.Files.deleteIfExists(Paths.get(tempDiskImagePath));
                } catch (Exception e) {
                    FileLogger.log(FileLogger.LogLevel.ERROR, "Failed to delete temp fs image");
                }
            }
            if (customDiskImagePath != null && PlatformEnvironment.getManager().isWeb()) {
                try {
                    java.nio.file.Files.deleteIfExists(Paths.get(customDiskImagePath));
                } catch (Exception e) {
                    FileLogger.log(FileLogger.LogLevel.ERROR, "Failed to delete custom fs image");
                }
            }
        });

        kernel.start();
    }

    private void initializeSimulation(MemoryMode mode) throws URISyntaxException {
        String file_separator = System.getProperty("file.separator");
        FileLogger.log("GUI: Initializing Simulation Hardware in " + mode + " mode...");

        computer = new RV32Computer(1024 * 1024 * 8, Integer.MAX_VALUE, mode);
        kernel = computer.getKernel();

        // KERNEL CONFIGURATION
        kernel.getConfig().setSchedulerType(KernelConfig.SchedulerType.ROUND_ROBIN);
        kernel.setTimeSlice(1000);

        // MOUNT FILE SYSTEM
        String imgPath;
        if (customDiskImagePath != null) {
            imgPath = customDiskImagePath;
        } else {
            String desktopPath = "src/main/resources/fs.img";
            if (!new File(desktopPath).exists()) {
                desktopPath = "app/src/main/resources/fs.img";
            }
            if (PlatformEnvironment.getManager().isWeb()) {
                try (java.io.InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("fs.img")) {
                    if (is == null) {
                        throw new java.io.FileNotFoundException("fs.img not found in resources");
                    }
                    java.nio.file.Path temp = java.nio.file.Files.createTempFile("fs_", ".img");
                    java.nio.file.Files.copy(is, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    tempDiskImagePath = temp.toString();
                    imgPath = tempDiskImagePath;
                } catch (Exception e) {
                    FileLogger.log(FileLogger.LogLevel.ERROR, "Failed to create temp fs.img from resources: " + e.getMessage());
                    imgPath = desktopPath;
                }
            } else {
                imgPath = desktopPath;
            }
        }
        
        File diskImage = new File(imgPath);
        if (diskImage.exists()) {
            kernel.mountFileSystem(imgPath);
            if (FileLogger.isLoggable(FileLogger.LogLevel.INFO))
                FileLogger.log("GUI: Disk image '" + imgPath + "' mounted.");
        } else {
            if (FileLogger.isLoggable(FileLogger.LogLevel.INFO))
                FileLogger.log(FileLogger.LogLevel.INFO,
                        "GUI: fs.img not found at " + imgPath + ". File system calls will fail.");
        }

        // LAUNCH INIT PROCESS
        String resourcePath = "user_programs/init";
        try (java.io.InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                if (FileLogger.isLoggable(FileLogger.LogLevel.DEBUG))
                    FileLogger.log(FileLogger.LogLevel.DEBUG,
                            "GUI: Found init in resources. Reading into memory...");
                byte[] elfData = is.readAllBytes();
                kernel.createTask(elfData, "init");
                if (FileLogger.isLoggable(FileLogger.LogLevel.DEBUG))
                    FileLogger.log("GUI: Init task created (PID 1).");
            } else {
                // Fallback to old path for backwards compatibility
                String oldPath = ".." + file_separator + "User_Program_ELF" + file_separator + "init.elf";
                File oldFile = new File(oldPath);
                if (oldFile.exists()) {
                    if (FileLogger.isLoggable(FileLogger.LogLevel.DEBUG))
                        FileLogger.log("GUI: Using legacy ELF path: " + oldPath);
                    kernel.createTask(oldPath);
                    if (FileLogger.isLoggable(FileLogger.LogLevel.DEBUG))
                        FileLogger.log("GUI: Init task created from legacy path.");
                } else {
                    if (FileLogger.isLoggable(FileLogger.LogLevel.ERROR))
                        FileLogger.log(FileLogger.LogLevel.ERROR,
                                "GUI: Error - init.elf NOT FOUND at " + oldPath);
                }
            }
        } catch (Exception e) {
            if (FileLogger.isLoggable(FileLogger.LogLevel.ERROR))
                FileLogger.log(FileLogger.LogLevel.ERROR,
                        "GUI: Failed to load Init task: " + e.getMessage());
            FileLogger.log(e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
