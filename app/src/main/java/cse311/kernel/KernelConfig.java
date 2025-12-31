package cse311.kernel;

/**
 * Kernel configuration class
 * Allows easy customization of kernel behavior
 */
public class KernelConfig {
    public enum SchedulerType {
        ROUND_ROBIN,
        COOPERATIVE,
        PRIORITY
    }

    private int CORE_COUNT = 4;

    // Scheduling configuration
    private SchedulerType schedulerType = SchedulerType.ROUND_ROBIN;
    private int timeSlice = 3; // Instructions per time slice

    // Memory configuration
    private int maxProcesses = 64;
    private int stackSize = 8192; // 8KB stack per process

    // System call configuration
    private boolean enableDebugSyscalls = true;
    private boolean enableFileSyscalls = true;

    // I/O configuration
    private int uartBufferSize = 256;

    // Constructors
    public KernelConfig() {
    }

    public KernelConfig(SchedulerType schedulerType, int timeSlice) {
        this.schedulerType = schedulerType;
        this.timeSlice = timeSlice;
    }

    // Getters and setters
    public SchedulerType getSchedulerType() {
        return schedulerType;
    }

    public void setSchedulerType(SchedulerType schedulerType) {
        this.schedulerType = schedulerType;
    }

    public int getTimeSlice() {
        return timeSlice;
    }

    public void setTimeSlice(int timeSlice) {
        this.timeSlice = timeSlice;
    }

    public int getMaxProcesses() {
        return maxProcesses;
    }

    public void setMaxProcesses(int maxProcesses) {
        this.maxProcesses = maxProcesses;
    }

    public int getStackSize() {
        return stackSize;
    }

    public void setStackSize(int stackSize) {
        this.stackSize = stackSize;
    }

    public boolean isEnableDebugSyscalls() {
        return enableDebugSyscalls;
    }

    public void setEnableDebugSyscalls(boolean enableDebugSyscalls) {
        this.enableDebugSyscalls = enableDebugSyscalls;
    }

    public boolean isEnableFileSyscalls() {
        return enableFileSyscalls;
    }

    public void setEnableFileSyscalls(boolean enableFileSyscalls) {
        this.enableFileSyscalls = enableFileSyscalls;
    }

    public int getUartBufferSize() {
        return uartBufferSize;
    }

    public void setUartBufferSize(int uartBufferSize) {
        this.uartBufferSize = uartBufferSize;
    }

    public int getCoreCount() {
        return CORE_COUNT;
    }

    public void setCoreCount(int coreCount) {
        CORE_COUNT = coreCount;
    }
}