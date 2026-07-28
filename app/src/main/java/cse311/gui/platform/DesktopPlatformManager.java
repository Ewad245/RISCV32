package cse311.gui.platform;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import java.io.File;
import java.util.function.Consumer;

public class DesktopPlatformManager implements PlatformManager {
    
    @Override
    public void showInfoAlert(String title, String header, String content, Window owner) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    @Override
    public void showErrorAlert(String title, String header, String content, Window owner) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    @Override
    public void showConfirmationAlert(String title, String header, String content, Window owner, Runnable onConfirm) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK && onConfirm != null) {
                onConfirm.run();
            }
        });
    }
    
    @Override
    public void chooseOpenFile(Node node, String title, String extName, String ext, Consumer<File> onFileReady) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(extName, ext));
        Window owner = (node != null && node.getScene() != null) ? node.getScene().getWindow() : null;
        File selectedFile = fileChooser.showOpenDialog(owner);
        if (selectedFile != null && onFileReady != null) {
            onFileReady.accept(selectedFile);
        }
    }
    
    @Override
    public void exit(Window window) {
        if (window != null) {
            window.hide();
        }
        Platform.exit();
    }
    
    @Override
    public void registerSessionCleanup(Window window, Runnable cleanup) {
        // Not needed for desktop as the JVM exits on window close.
    }

    @Override
    public boolean isWeb() {
        return false;
    }
}
