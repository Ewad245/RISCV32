package cse311;

/**
 * Reasons why a task might be waiting
 */
public enum WaitReason {
    /**
     * Task is not waiting
     */
    NONE,
    
    /**
     * Task is waiting for UART input
     */
    UART_INPUT,
    
    /**
     * Task is waiting for a timer to expire
     */
    TIMER,
    
    /**
     * Task is waiting for another task to exit
     */
    PROCESS_EXIT,
    
    /**
     * Task is waiting for file I/O
     */
    FILE_IO,
    
    /**
     * Task is waiting for memory allocation
     */
    MEMORY,
    
    /**
     * Task is waiting for a custom condition
     */
    CUSTOM
}