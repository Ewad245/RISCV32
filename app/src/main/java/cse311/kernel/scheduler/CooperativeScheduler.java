package cse311.kernel.scheduler;

import cse311.kernel.process.Task;
import cse311.kernel.process.TaskState;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Cooperative scheduler implementation
 * Tasks run until they voluntarily yield (via system call)
 */
public class CooperativeScheduler extends Scheduler {
    private final Queue<Task> readyQueue = new ConcurrentLinkedQueue<>();
    private final Set<Task> queuedTasks = Collections.synchronizedSet(new HashSet<>());
    private Task currentTask = null;

    // Statistics
    private int totalSchedules = 0;
    private int contextSwitches = 0;
    private long totalSchedulingTime = 0;

    public CooperativeScheduler() {
        super(Integer.MAX_VALUE); // No time limit in cooperative scheduling
    }

    @Override
    public Task schedule() {
        long startTime = System.nanoTime();
        totalSchedules++;

        // Simple FIFO polling for the next ready task
        Task nextTask = readyQueue.poll();

        if (nextTask != null) {
            queuedTasks.remove(nextTask);
            if (currentTask != nextTask) {
                contextSwitches++;
                currentTask = nextTask;
            }
        }

        totalSchedulingTime += System.nanoTime() - startTime;
        return nextTask;
    }

    @Override
    public void addTask(Task task) {
        if (task.getState() != TaskState.READY) {
            task.setState(TaskState.READY);
        }
        if (queuedTasks.add(task)) {
            readyQueue.offer(task);
        }
    }

    @Override
    public void removeTask(Task task) {
        readyQueue.remove(task);
        queuedTasks.remove(task);
        if (currentTask == task) {
            currentTask = null;
        }
    }

    @Override
    public SchedulerStats getStats() {
        return new SchedulerStats(totalSchedules, contextSwitches,
                totalSchedulingTime, "Cooperative");
    }

    /**
     * Get the number of tasks being managed
     */
    public int getTaskCount() {
        return readyQueue.size();
    }

    @Override
    public Collection<Task> getReadyTasks() {
        return Collections.unmodifiableCollection(readyQueue);
    }
}