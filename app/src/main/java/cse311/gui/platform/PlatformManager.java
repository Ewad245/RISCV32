package cse311.gui.platform;

import javafx.stage.Window;
import java.io.File;
import java.util.function.Consumer;

public interface PlatformManager {
    
    /**
     * Show an information alert.
     */
    void showInfoAlert(String title, String header, String content, Window owner);
    
    /**
     * Show an error alert.
     */
    void showErrorAlert(String title, String header, String content, Window owner);
    
    /**
     * Show a confirmation alert with a callback for when the user confirms (clicks OK).
     */
    void showConfirmationAlert(String title, String header, String content, Window owner, Runnable onConfirm);
    
    /**
     * Ask the user to choose an existing file. In desktop, this uses FileChooser.
     * In web, it uses a FileUploader attached to a specific UI node (but for simplicity here,
     * we will see if we can adapt it or we might need the caller to provide the Node).
     * 
     * @param node The Node (Button, MenuItem) triggering this. Needed for JPro FileUploader.
     * @param title Title of the dialog (desktop only)
     * @param extName Extension description
     * @param ext Extension filter (e.g., "*.img")
     * @param onFileReady Callback executed when the file is available
     */
    void chooseOpenFile(javafx.scene.Node node, String title, String extName, String ext, Consumer<File> onFileReady);
    
    /**
     * Cleanly exit or redirect the session.
     */
    void exit(Window window);
    
    /**
     * Register a callback to be run when the user session closes.
     */
    void registerSessionCleanup(Window window, Runnable cleanup);
    
    /**
     * Checks if the application is running in a web environment.
     * @return true if running on the web, false otherwise.
     */
    boolean isWeb();
}
