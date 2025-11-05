package cse311.kernel.scheduler;

import cse311.Task;
import cse311.TaskState;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Priority-based scheduler implementation
 * Tasks with higher priority values are scheduled first
 */
public class PriorityScheduler extends Scheduler {
    private final PriorityBlockingQueue<Task> readyQueue = new PriorityBlockingQueue<>(
        16, Comparator.comparingInt(Task::getPriority).reversed()
    );
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
    public Task schedule(Collection<Task> tasks) {
        long startTime = System.nanoTime();
        totalSchedules++;
        
        // Add any newly ready tasks to the queue
        for (Task task : tasks) {
            if (task.getState() == TaskState.READY && !readyQueue.contains(task)) {
                readyQueue.offer(task);
            }
        }
        
        // Remove non-ready tasks from the queue
        readyQueue.removeIf(t -> t.getState() != TaskState.READY);
        
        // Get the highest priority task
        Task nextTask = readyQueue.poll();
        
        // If we have a task, add it back to the queue for next time
        // (unless it's about to be terminated)
        if (nextTask != null && nextTask.getState() == TaskState.READY) {
            readyQueue.offer(nextTask);
            
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
        if (task.getState() == TaskState.READY && !readyQueue.contains(task)) {
            readyQueue.offer(task);
        }
    }
    
    @Override
    public void removeTask(Task task) {
        readyQueue.remove(task);
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
}