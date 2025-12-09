package cse311.kernel.memory;

import cse311.Exception.MemoryAccessException;

/**
 * Abstraction for the hardware/software MMU.
 * Supports both Contiguous (Base/Limit) and Non-Contiguous (Paging).
 */
public interface MemoryManagementUnit {
    /**
     * Translates a logical address to a physical address.
     * 
     * @param pid         The process ID requesting access.
     * @param logicalAddr The virtual/logical address.
     * @return The physical address in SimpleMemory.
     * @throws MemoryAccessException if access is illegal (Segmentation Fault or
     *                               Page Fault).
     */
    int translate(int pid, int logicalAddr) throws MemoryAccessException;

    /**
     * Allocates memory for a process based on the specific strategy.
     * 
     * @param pid       Process ID.
     * @param sizeBytes Size required.
     * @return True if allocation successful.
     */
    boolean allocateMemory(int pid, int sizeBytes);

    /**
     * Frees memory for a process.
     */
    void freeMemory(int pid);

    /**
     * Handle Context Switching (loading base/limit regs or SATP).
     */
    void switchContext(int pid);

    /**
     * For Contiguous: Triggers Compaction to fix External Fragmentation.
     *
     */
    void compact();
}
