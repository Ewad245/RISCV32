package cse311.kernel.memory;

import java.util.List;

import cse311.Exception.MemoryAccessException;
import cse311.kernel.process.ProgramInfo;

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
    ProgramInfo loadProgram(int pid, byte[] elfData) throws Exception;

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
     * Attempts to expand the physical or virtual heap space.
     * For contiguous memory, this is a no-op (memory is pre-allocated).
     * For paged memory, this updates the heap limit so the pager knows
     * which addresses are valid for demand allocation.
     *
     * @param pid          The process ID.
     * @param currentBreak The current program break address.
     * @param newBreak     The requested new program break address.
     * @return true if successful, false if out of memory.
     */
    boolean expandHeap(int pid, int currentBreak, int newBreak) throws MemoryAccessException;

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
