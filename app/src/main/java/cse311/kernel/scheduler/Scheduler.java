package cse311.kernel.scheduler;

import java.util.Collection;

import cse311.kernel.process.Task;

/**
 * Abstract base class for all schedulers
 * Defines the interface that all scheduling algorithms must implement
 */
public abstract class Scheduler {
    protected int timeSlice;

    public Scheduler(int timeSlice) {
        this.timeSlice = timeSlice;
    }

    /**
     * Retrieve the next task from the Ready Queue.
     */
    public abstract Task schedule();

    /**
     * Add a new task to the scheduler
     * 
     * @param task The task to add
     */
    public abstract void addTask(Task task);

    /**
     * Remove a task from the scheduler
     * 
     * @param task The task to remove
     */
    public abstract void removeTask(Task task);

    /**
     * Get the time slice for this scheduler
     * 
     * @return Time slice in instructions
     */
    public int getTimeSlice() {
        return timeSlice;
    }

    /**
     * Set the time slice for this scheduler
     * 
     * @param timeSlice Time slice in instructions
     */
    public void setTimeSlice(int timeSlice) {
        this.timeSlice = timeSlice;
    }

    /**
     * Get scheduler statistics
     * 
     * @return Scheduler-specific statistics
     */
    public abstract SchedulerStats getStats();

    /**
     * Get a collection of tasks currently in the ready queue.
     * This is primarily for visualization/observability.
     */
    public abstract Collection<Task> getReadyTasks();
}