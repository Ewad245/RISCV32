package cse311.example;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.PriorityQueue;

import com.google.auto.service.AutoService;
import cse311.kernel.process.Task;
import cse311.kernel.scheduler.Scheduler;
import cse311.kernel.scheduler.SchedulerStats;

/**
 * Example SPI custom priority scheduler.
 * Picks ready task with highest priority (lowest priority number).
 */
@AutoService(Scheduler.class)
public class PrioritySchedulerExample extends Scheduler {

    private final PriorityQueue<Task> readyQueue = new PriorityQueue<>(
            Comparator.comparingInt(Task::getPriority));
    private int scheduleCount = 0;

    public PrioritySchedulerExample() {
        super();
    }

    @Override
    public synchronized Task schedule() {
        if (readyQueue.isEmpty()) {
            return null;
        }
        scheduleCount++;
        return readyQueue.poll();
    }

    @Override
    public synchronized void addTask(Task task) {
        if (task != null && !readyQueue.contains(task)) {
            readyQueue.add(task);
        }
    }

    @Override
    public synchronized void removeTask(Task task) {
        if (task != null) {
            readyQueue.remove(task);
        }
    }

    @Override
    public synchronized Collection<Task> getReadyTasks() {
        return Collections.unmodifiableCollection(readyQueue);
    }

    @Override
    public synchronized SchedulerStats getStats() {
        return new SchedulerStats(scheduleCount, 0, 0, "CustomPriorityScheduler");
    }
}

