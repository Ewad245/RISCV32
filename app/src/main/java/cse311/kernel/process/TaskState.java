package cse311.kernel.process;

/**
 * Task states in the kernel
 */
public enum TaskState {
    /**
     * Task is ready to run and waiting to be scheduled
     */
    READY,

    /**
     * Task is currently running on the CPU
     */
    RUNNING,

    /**
     * Task is waiting for some event (I/O, timer, etc.)
     */
    WAITING,

    /**
     * Task has finished execution or was killed
     */
    TERMINATED
}