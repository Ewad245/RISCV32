package cse311.gui.platform;

import com.jpro.webapi.WebAPI;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import java.io.File;
import java.util.function.Consumer;

public class WebPlatformManager implements PlatformManager {
    
    @Override
    public void showInfoAlert(String title, String header, String content, Window owner) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        // Non-blocking in JPro
        alert.show();
    }
    
    @Override
    public void showErrorAlert(String title, String header, String content, Window owner) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.show();
    }
    
    @Override
    public void showConfirmationAlert(String title, String header, String content, Window owner, Runnable onConfirm) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.show();
        alert.resultProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == ButtonType.OK && onConfirm != null) {
                onConfirm.run();
            }
        });
    }
    
    @Override
    public void chooseOpenFile(Node node, String title, String extName, String ext, Consumer<File> onFileReady) {
        // Since it's often triggered from a MenuItem (which is not a Node), 
        // we create a custom Dialog with a Button that acts as the upload trigger.
        Dialog<File> dialog = new Dialog<>();
        if (node != null && node.getScene() != null) {
            dialog.initOwner(node.getScene().getWindow());
        }
        dialog.setTitle(title);
        dialog.setHeaderText("Select file to upload (" + ext + ")");
        
        Button uploadBtn = new Button("Choose File...");
        VBox vbox = new VBox(10, uploadBtn);
        vbox.setStyle("-fx-padding: 20; -fx-alignment: center;");
        dialog.getDialogPane().setContent(vbox);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL);
        
        dialog.setResultConverter(dialogButton -> null);
        
        dialog.show();
        
        // We need the scene to be shown so WebAPI can attach to it, 
        // but JPro file uploader can wrap the button before it's shown if we use getWebAPIs
        WebAPI webAPI = WebAPI.getWebAPI(dialog.getOwner() != null ? dialog.getOwner() : (node != null && node.getScene() != null ? node.getScene().getWindow() : null));
        if (webAPI != null) {
            try {
                Object uploaderObj = webAPI.getClass().getMethod("makeFileUploadNode", Node.class).invoke(webAPI, uploadBtn);
                
                try {
                    uploaderObj.getClass().getMethod("setSelectFileOnClick", boolean.class).invoke(uploaderObj, true);
                } catch (NoSuchMethodException e) {
                    // Ignore: Method may be removed or unnecessary in newer JPro versions
                }
                
                Consumer<File> onFile = file -> dialog.setResult(file);
                
                boolean methodFound = false;
                for (java.lang.reflect.Method m : uploaderObj.getClass().getMethods()) {
                    System.out.println("JPRO_DEBUG: Method " + m.getName() + " params " + java.util.Arrays.toString(m.getParameterTypes()));
                    if (m.getName().equals("setOnFileSelected") && m.getParameterCount() == 1) {
                        Class<?> paramType = m.getParameterTypes()[0];
                        if (paramType == Consumer.class) {
                            m.invoke(uploaderObj, onFile);
                            methodFound = true;
                            // don't break yet, we want to print all methods
                        } else if (paramType.isInterface()) {
                            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                                paramType.getClassLoader(),
                                new Class<?>[]{paramType},
                                (p, method, args) -> {
                                    if (args != null && args.length > 0 && args[0] instanceof File) {
                                        onFile.accept((File) args[0]);
                                    }
                                    return null;
                                }
                            );
                            m.invoke(uploaderObj, proxy);
                            methodFound = true;
                            // don't break yet, print all methods
                        }
                    }
                }
                if (!methodFound) {
                    java.util.logging.Logger.getLogger(WebPlatformManager.class.getName()).log(java.util.logging.Level.SEVERE, "Could not find setOnFileSelected method!");
                }
            } catch (Exception e) {
                java.util.logging.Logger.getLogger(WebPlatformManager.class.getName()).log(java.util.logging.Level.SEVERE, "Failed to setup file upload", e);
            }
        }
        dialog.resultProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && onFileReady != null) {
                onFileReady.accept(newVal);
            }
        });
    }
    
    @Override
    public void exit(Window window) {
        WebAPI webAPI = WebAPI.getWebAPI(window);
        if (webAPI != null) {
            webAPI.executeScript("window.close();");
        }
    }
    
    @Override
    public void registerSessionCleanup(Window window, Runnable cleanup) {
        WebAPI webAPI = WebAPI.getWebAPI(window);
        if (webAPI != null) {
            window.setOnCloseRequest(e -> cleanup.run());
        }
    }

    @Override
    public boolean isWeb() {
        return true;
    }
}
