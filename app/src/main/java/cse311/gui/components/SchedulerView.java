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
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class SchedulerView extends VBox {

    private final Kernel kernel;

    @FXML
    private ListView<String> readyList;
    @FXML
    private ListView<String> ioList;
    @FXML
    private ListView<String> sleepList;
    @FXML
    private ListView<String> cvWaitList;

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
            updateCvWaitList();
        });
    }

    private void updateList(ListView<String> listView, Collection<Task> tasks) {
        if (tasks == null)
            return;

        // Group threads by TGID for better visualization
        Map<Integer, List<Task>> groups = new HashMap<>();
        for (Task task : tasks) {
            int tgid = task.getTgid();
            groups.computeIfAbsent(tgid, k -> new ArrayList<>()).add(task);
        }

        // Build formatted list with grouping
        List<String> items = new ArrayList<>();
        for (Map.Entry<Integer, List<Task>> entry : groups.entrySet()) {
            int tgid = entry.getKey();
            List<Task> groupTasks = entry.getValue();

            // Sort threads within group by PID
            groupTasks.sort((a, b) -> Integer.compare(a.getId(), b.getId()));

            // Add header for thread groups with multiple threads
            if (groupTasks.size() > 1) {
                items.add(String.format("[Thread Group TGID=%d] (%d threads)", tgid, groupTasks.size()));
                for (Task task : groupTasks) {
                    items.add(String.format("  +-- PID %d [%s] (%s)", task.getId(), task.getName(), task.getState()));
                }
            } else {
                // Single thread/process - show normally
                Task task = groupTasks.get(0);
                items.add(String.format("PID %d [%s] (%s)", task.getId(), task.getName(), task.getState()));
            }
        }

        listView.setItems(FXCollections.observableArrayList(items));
    }

    private void updateCvWaitList() {
        var cvWaitTasks = kernel.getConditionVariableWaitQueue();
        if (cvWaitTasks == null || cvWaitTasks.isEmpty()) {
            cvWaitList.setItems(FXCollections.emptyObservableList());
            return;
        }

        var items = cvWaitTasks.stream()
                .map(t -> String.format("PID %d [%s] (WAITING on CV)", t.getId(), t.getName()))
                .collect(Collectors.toList());

        cvWaitList.setItems(FXCollections.observableArrayList(items));
    }
}
