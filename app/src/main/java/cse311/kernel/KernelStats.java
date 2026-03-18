package cse311.kernel;

/**
 * Kernel statistics data class
 */
public class KernelStats {
    public final int totalProcesses;
    public final int runningProcesses;
    public final int readyProcesses;
    public final int waitingProcesses;
    public final int terminatedProcesses;

    public KernelStats(int totalProcesses, int runningProcesses, int readyProcesses,
            int waitingProcesses, int terminatedProcesses) {
        this.totalProcesses = totalProcesses;
        this.runningProcesses = runningProcesses;
        this.readyProcesses = readyProcesses;
        this.waitingProcesses = waitingProcesses;
        this.terminatedProcesses = terminatedProcesses;
    }
}