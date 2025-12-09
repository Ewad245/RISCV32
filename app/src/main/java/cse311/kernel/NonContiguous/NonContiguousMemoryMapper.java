package cse311.kernel.NonContiguous;

import cse311.MemoryManager;
import cse311.Exception.MemoryAccessException;

public interface NonContiguousMemoryMapper {
    /** Creates the Page Table or Segment Table for a process */
    void createAddressSpace(int pid);

    /** Destroys the tables */
    void destroyAddressSpace(int pid);

    /** Switches hardware context (SATP or Segment Registers) */
    void switchContext(int pid);

    /**
     * Maps a stack region and returns the virtual base address.
     * 
     * @return The Virtual Address where the stack starts.
     */
    int mapStack(int pid, int size) throws MemoryAccessException;

    /** Copies tables for fork() */
    void copyAddressSpace(int parentPid, int childPid) throws MemoryAccessException;

    /** Returns the underlying MemoryManager (PagedMemoryManager, etc.) */
    MemoryManager getMemoryInterface();
}
