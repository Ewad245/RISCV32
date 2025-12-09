package cse311.kernel.NonContiguous.paging;

import cse311.Exception.MemoryAccessException;

/**
 * Policy interface for virtual memory management.
 * Decides when and how to allocate or evict pages.
 */
public interface Pager {
    /**
     * Ensure that the given virtual address in the given address space
     * is resident, allocating or fetching it if needed.
     * Return the physical frame number, or -2 for MMIO (UART).
     */
    int ensureResident(AddressSpace as, int va, VmAccess access)
            throws MemoryAccessException;
}