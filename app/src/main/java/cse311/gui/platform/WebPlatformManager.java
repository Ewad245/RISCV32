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
                    uploaderObj.getClass().getMethod("setSelectFileOnClick", Boolean.class).invoke(uploaderObj, Boolean.TRUE);
                } catch (NoSuchMethodException e) {
                    java.util.logging.Logger.getLogger(WebPlatformManager.class.getName()).log(java.util.logging.Level.FINE, "Method setSelectFileOnClick not found", e);
                }
                
                Consumer<File> onFileSelected = file -> {
                    try {
                        // JPro deletes temp files when the dialog containing the upload node closes.
                        // We must copy it to a persistent temp file before closing the dialog.
                        java.nio.file.Path tempDest = java.nio.file.Files.createTempFile("jpro_upload_", "_" + file.getName());
                        java.nio.file.Files.copy(file.toPath(), tempDest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        File permanentFile = tempDest.toFile();
                        permanentFile.deleteOnExit();
                        
                        javafx.application.Platform.runLater(() -> {
                            dialog.setResult(permanentFile);
                            dialog.close();
                        });
                    } catch (Exception e) {
                        java.util.logging.Logger.getLogger(WebPlatformManager.class.getName())
                            .log(java.util.logging.Level.SEVERE, "Failed to copy uploaded file", e);
                        javafx.application.Platform.runLater(() -> {
                            dialog.setResult(file);
                            dialog.close();
                        });
                    }
                };
                
                boolean methodFound = false;
                for (java.lang.reflect.Method m : uploaderObj.getClass().getMethods()) {
                    if ("setOnFileSelected".equals(m.getName()) && m.getParameterCount() == 1) {
                        Class<?> paramType = m.getParameterTypes()[0];
                        if (paramType == Consumer.class) {
                            m.invoke(uploaderObj, onFileSelected);
                            methodFound = true;
                        } else if (paramType.isInterface()) {
                            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                                Thread.currentThread().getContextClassLoader(),
                                new Class<?>[]{paramType},
                                (p, method, args) -> {
                                    if (args != null && args.length > 0) {
                                        if (args[0] instanceof File) {
                                            onFileSelected.accept((File) args[0]);
                                        } else if (args[0] instanceof String) {
                                            onFileSelected.accept(new File((String) args[0]));
                                        }
                                    }
                                    return null;
                                }
                            );
                            m.invoke(uploaderObj, proxy);
                            methodFound = true;
                        }
                    }
                }
                if (!methodFound) {
                    java.util.logging.Logger.getLogger(WebPlatformManager.class.getName()).log(java.util.logging.Level.SEVERE, "Could not find setOnFileSelected method!");
                }
            } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
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
