package cse311.gui.components;

import cse311.kernel.Kernel;
import cse311.kernel.process.Task;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.Collection;
import java.util.stream.Collectors;

public class SchedulerView extends VBox {

    private final Kernel kernel;

    @FXML
    private ListView<String> readyList;
    @FXML
    private ListView<String> ioList;
    @FXML
    private ListView<String> sleepList;

    public SchedulerView(Kernel kernel) {
        this.kernel = kernel;

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/SchedulerView.fxml"));
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load SchedulerView.fxml", e);
        }
    }

    public void update() {
        if (kernel == null)
            return;

        // Ensure UI updates happen on JavaFX thread
        Platform.runLater(() -> {
            updateList(readyList, kernel.getReadyQueue());
            updateList(ioList, kernel.getIoWaitQueue());
            updateList(sleepList, kernel.getSleepWaitQueue());
        });
    }

    private void updateList(ListView<String> listView, Collection<Task> tasks) {
        if (tasks == null)
            return;

        // Convert tasks to string representation
        var items = tasks.stream()
                .map(t -> String.format("PID %d [%s] (%s)", t.getId(), t.getName(), t.getState()))
                .collect(Collectors.toList());

        listView.setItems(FXCollections.observableArrayList(items));
    }
}
