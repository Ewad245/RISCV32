package cse311.kernel.memory;

import java.util.List;

import cse311.Exception.MemoryAccessException;

/**
 * Strategy interface for managing process memory lifecycles.
 * Decouples TaskManager from specific memory implementations (Paging vs
 * Contiguous).
 */
public interface ProcessMemoryCoordinator {

    /**
     * Allocates the necessary memory structure for a process.
     * 
     * @param pid       The process ID.
     * @param sizeBytes Size required (Critical for Contiguous, ignored/quota for
     *                  Paging).
     * @return A MemoryLayout containing stack/heap locations.
     */
    MemoryLayout allocateMemory(int pid, int sizeBytes) throws MemoryAccessException;

    /**
     * Loads the ELF binary data into the allocated memory.
     * 
     * @return The entry point address.
     */
    int loadProgram(int pid, byte[] elfData) throws Exception;

    /**
     * Frees resources when a process dies.
     */
    void freeMemory(int pid);

    /**
     * Copies memory from parent to child (for fork).
     */
    void copyMemory(int parentPid, int childPid) throws MemoryAccessException;

    /**
     * NEW: Switch the hardware memory context to this process.
     * Replaces the manual check in Kernel.java.
     */
    void switchContext(int pid);

    /**
     * NEW: Set up the stack with command line arguments.
     * Replaces manual stack manipulation in SystemCallHandler.
     */
    int setupStack(int pid, List<String> args, MemoryLayout layout) throws MemoryAccessException;

    /**
     * Data Transfer Object (DTO) to return memory details to TaskManager.
     */
    class MemoryLayout {
        public final int stackBase;
        public final int stackSize;

        public MemoryLayout(int stackBase, int stackSize) {
            this.stackBase = stackBase;
            this.stackSize = stackSize;
        }
    }
}
