package cse311.kernel.scheduler;

import cse311.kernel.process.Task;
import cse311.kernel.process.TaskState;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Round-robin scheduler implementation
 * Tasks are scheduled in a circular fashion with equal time slices
 */
public class RoundRobinScheduler extends Scheduler {
    private final Queue<Task> readyQueue = new ConcurrentLinkedQueue<>();
    private final Set<Task> queuedTasks = Collections.synchronizedSet(new HashSet<>());
    private Task currentTask = null;

    // Statistics
    private int totalSchedules = 0;
    private int contextSwitches = 0;
    private long totalSchedulingTime = 0;

    public RoundRobinScheduler(int timeSlice) {
        super(timeSlice);
    }

    @Override
    public Task schedule() {
        long startTime = System.nanoTime();
        totalSchedules++;

        // Get the next task from the head of the queue (FIFO)
        // This removes it from the queue.
        Task nextTask = readyQueue.poll();

        if (nextTask != null) {
            queuedTasks.remove(nextTask);
            // Context switch tracking
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
        // Atomic check-and-add to prevent duplicates
        if (task.getState() == TaskState.READY && queuedTasks.add(task)) {
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
                totalSchedulingTime, "Round Robin");
    }

    /**
     * Get the current ready queue size
     */
    public int getReadyQueueSize() {
        return readyQueue.size();
    }

    /**
     * Get a snapshot of the ready queue
     */
    public List<Task> getReadyQueueSnapshot() {
        return new ArrayList<>(readyQueue);
    }

    @Override
    public Collection<Task> getReadyTasks() {
        return Collections.unmodifiableCollection(readyQueue);
    }
}