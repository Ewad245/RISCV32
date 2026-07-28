package cse311.kernel.scheduler;

import java.util.Collection;

import cse311.kernel.process.Task;

/**
 * Abstract base class for all schedulers
 * Defines the interface that all scheduling algorithms must implement
 */
public abstract class Scheduler {
    protected int timeSlice;

    /**
     * Creates a scheduler with the given time slice.
     */
    public Scheduler(int timeSlice) {
        this.timeSlice = timeSlice;
    }

    /**
     * Protected no-arg constructor for SPI plugin implementations.
     * The time slice must be set via {@link #setTimeSlice(int)} before use.
     */
    protected Scheduler() {
        this.timeSlice = 1;
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

    private static final int SINGLE_TASK_THRESHOLD = 1;
    private static final int SINGLE_TASK_TIME_SLICE = 100_000;
    private static final int MIN_MULTI_TASK_TIME_SLICE = 20_000;

    /**
     * Get the number of tasks currently waiting in the ready queue.
     */
    public int getReadyTaskCount() {
        Collection<Task> tasks = getReadyTasks();
        return (tasks != null) ? tasks.size() : 0;
    }

    /**
     * Get adaptive time slice based on task load.
     * Expands time slice up to 100,000 instructions when single process is active.
     */
    public int getAdaptiveTimeSlice() {
        int count = getReadyTaskCount();
        if (count <= SINGLE_TASK_THRESHOLD) {
            return SINGLE_TASK_TIME_SLICE;
        }
        return Math.max(timeSlice, MIN_MULTI_TASK_TIME_SLICE);
    }
}