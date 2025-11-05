package cse311.kernel.scheduler;

import cse311.Task;
import cse311.TaskState;
import java.util.*;

/**
 * Cooperative scheduler implementation
 * Tasks run until they voluntarily yield (via system call)
 */
public class CooperativeScheduler extends Scheduler {
    private final List<Task> tasks = new ArrayList<>();
    private int currentIndex = 0;
    private Task currentTask = null;
    
    // Statistics
    private int totalSchedules = 0;
    private int contextSwitches = 0;
    private long totalSchedulingTime = 0;
    
    public CooperativeScheduler() {
        super(Integer.MAX_VALUE); // No time limit in cooperative scheduling
    }
    
    @Override
    public Task schedule(Collection<Task> allTasks) {
        long startTime = System.nanoTime();
        totalSchedules++;
        
        // Update our task list
        updateTaskList(allTasks);
        
        if (tasks.isEmpty()) {
            totalSchedulingTime += System.nanoTime() - startTime;
            return null;
        }
        
        // If current task is still running, keep it
        if (currentTask != null && currentTask.getState() == TaskState.RUNNING) {
            totalSchedulingTime += System.nanoTime() - startTime;
            return currentTask;
        }
        
        // Find the next ready task
        Task nextTask = findNextReadyTask();
        
        // Count context switch if we're switching to a different task
        if (currentTask != nextTask) {
            contextSwitches++;
            currentTask = nextTask;
        }
        
        totalSchedulingTime += System.nanoTime() - startTime;
        return nextTask;
    }
    
    private void updateTaskList(Collection<Task> allTasks) {
        // Remove terminated tasks
        tasks.removeIf(t -> t.getState() == TaskState.TERMINATED);
        
        // Add new tasks
        for (Task task : allTasks) {
            if (!tasks.contains(task) && task.getState() != TaskState.TERMINATED) {
                tasks.add(task);
            }
        }
    }
    
    private Task findNextReadyTask() {
        if (tasks.isEmpty()) {
            return null;
        }
        
        // Start from current index and look for a ready task
        int attempts = 0;
        while (attempts < tasks.size()) {
            Task task = tasks.get(currentIndex);
            currentIndex = (currentIndex + 1) % tasks.size();
            attempts++;
            
            if (task.getState() == TaskState.READY) {
                return task;
            }
        }
        
        return null; // No ready tasks
    }
    
    @Override
    public void addTask(Task task) {
        if (!tasks.contains(task)) {
            tasks.add(task);
        }
    }
    
    @Override
    public void removeTask(Task task) {
        int index = tasks.indexOf(task);
        if (index != -1) {
            tasks.remove(index);
            // Adjust current index if necessary
            if (currentIndex > index) {
                currentIndex--;
            } else if (currentIndex >= tasks.size()) {
                currentIndex = 0;
            }
        }
        
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
        return tasks.size();
    }
    
    /**
     * Get the current process index
     */
    public int getCurrentIndex() {
        return currentIndex;
    }
}