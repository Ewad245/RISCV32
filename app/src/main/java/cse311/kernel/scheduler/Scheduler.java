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
     * Select the next task to run
     * 
     * @param tasks Collection of all tasks
     * @return The task to run next, or null if no task is ready
     */
    public abstract Task schedule(Collection<Task> tasks);

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
}