package cse311.kernel.scheduler;

import cse311.kernel.process.Task;
import cse311.kernel.process.TaskState;

import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Priority-based scheduler implementation
 * Tasks with higher priority values are scheduled first
 */
public class PriorityScheduler extends Scheduler {
    // The Ready Queue: Explicitly managed inside the scheduler
    private final PriorityBlockingQueue<Task> readyQueue = new PriorityBlockingQueue<>(
            16, Comparator.comparingInt(Task::getPriority).reversed());
    private final Set<Task> queuedTasks = Collections.synchronizedSet(new HashSet<>());

    private Task currentTask = null;

    // Statistics
    private int totalSchedules = 0;
    private int contextSwitches = 0;
    private long totalSchedulingTime = 0;

    public PriorityScheduler() {
        super(1000); // Default time slice
    }

    public PriorityScheduler(int timeSlice) {
        super(timeSlice);
    }

    @Override
    public Task schedule() {
        long startTime = System.nanoTime();
        totalSchedules++;

        // Poll the highest priority task from the internal ready queue
        // This effectively removes it from the queue (dequeue)
        Task nextTask = readyQueue.poll();

        if (nextTask != null) {
            queuedTasks.remove(nextTask);
            // Count context switch if we're switching to a different task
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
        // Only add if not already present to avoid duplicates
        // Ensure state is READY before adding (defensive programming)
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
                totalSchedulingTime, "Priority");
    }

    /**
     * Get the current ready queue size
     */
    public int getReadyQueueSize() {
        return readyQueue.size();
    }

    /**
     * Get a snapshot of the ready queue ordered by priority
     */
    public List<Task> getReadyQueueSnapshot() {
        List<Task> snapshot = new ArrayList<>(readyQueue);
        snapshot.sort(Comparator.comparingInt(Task::getPriority).reversed());
        return snapshot;
    }

    /**
     * Get the highest priority among ready tasks
     */
    public int getHighestPriority() {
        Task highest = readyQueue.peek();
        return highest != null ? highest.getPriority() : -1;
    }

    @Override
    public Collection<Task> getReadyTasks() {
        return Collections.unmodifiableCollection(readyQueue);
    }
}