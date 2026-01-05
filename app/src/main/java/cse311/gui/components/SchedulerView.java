package cse311.gui.components;

import cse311.kernel.Kernel;
import cse311.kernel.process.Task;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

import java.util.Collection;
import java.util.stream.Collectors;

public class SchedulerView extends VBox {

    private final Kernel kernel;
    private final ListView<String> readyList;
    private final ListView<String> ioList;
    private final ListView<String> sleepList;

    public SchedulerView(Kernel kernel) {
        this.kernel = kernel;
        this.setSpacing(5);
        this.setPadding(new Insets(5));

        readyList = new ListView<>();
        VBox.setVgrow(readyList, javafx.scene.layout.Priority.ALWAYS);

        ioList = new ListView<>();
        VBox.setVgrow(ioList, javafx.scene.layout.Priority.ALWAYS);

        sleepList = new ListView<>();
        VBox.setVgrow(sleepList, javafx.scene.layout.Priority.ALWAYS);

        this.getChildren().addAll(
                new Label("Ready Queue:"), readyList,
                new Label("I/O Wait Queue:"), ioList,
                new Label("Sleep/Timer Queue:"), sleepList);
    }

    public void update() {
        if (kernel == null)
            return;

        updateList(readyList, kernel.getReadyQueue(), "READY");
        updateList(ioList, kernel.getIoWaitQueue(), "WAIT_IO");
        updateList(sleepList, kernel.getSleepWaitQueue(), "WAIT_TIME");
    }

    private void updateList(ListView<String> listView, Collection<Task> tasks, String defaultState) {
        if (tasks == null)
            return;

        // Convert tasks to string representation
        // PID [Name] (State)
        listView.setItems(FXCollections.observableArrayList(
                tasks.stream()
                        .map(t -> String.format("PID %d [%s] (%s)", t.getId(), t.getName(), t.getState()))
                        .collect(Collectors.toList())));
    }
}
