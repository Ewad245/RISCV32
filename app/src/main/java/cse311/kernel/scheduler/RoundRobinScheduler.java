package cse311.kernel.scheduler;

import cse311.Task;
import cse311.TaskState;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Round-robin scheduler implementation
 * Tasks are scheduled in a circular fashion with equal time slices
 */
public class RoundRobinScheduler extends Scheduler {
    private final Queue<Task> readyQueue = new ConcurrentLinkedQueue<>();
    private Task currentTask = null;
    
    // Statistics
    private int totalSchedules = 0;
    private int contextSwitches = 0;
    private long totalSchedulingTime = 0;
    
    public RoundRobinScheduler(int timeSlice) {
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
        
        // Get the next task from the queue
        Task nextTask = readyQueue.poll();
        
        // If we have a task, add it back to the end of the queue for next time
        if (nextTask != null) {
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
}