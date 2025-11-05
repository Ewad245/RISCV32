package cse311.kernel.scheduler;

/**
 * Scheduler statistics
 */
public class SchedulerStats {
    public final int totalSchedules;
    public final int contextSwitches;
    public final long totalSchedulingTime;
    public final String algorithmName;
    
    public SchedulerStats(int totalSchedules, int contextSwitches, 
                         long totalSchedulingTime, String algorithmName) {
        this.totalSchedules = totalSchedules;
        this.contextSwitches = contextSwitches;
        this.totalSchedulingTime = totalSchedulingTime;
        this.algorithmName = algorithmName;
    }
}