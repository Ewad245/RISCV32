package cse311.kernel.NonContiguous.paging;

import cse311.Exception.MemoryAccessException;

public final class AddressSpace {
    // Root of a 2-level page table (Sv32-like): 4KB pages
    final int pid;
    final PageDirectory root;

    AddressSpace(int pid) {
        this.pid = pid;
        this.root = new PageDirectory();
        this.memoryManager = null;
    }

    static final class PageDirectory {
        final PageTableEntry[] entries = new PageTableEntry[1024]; // L1 (Page Directory)
    }

    static final class PageTable {
        final PageTableEntry[] entries = new PageTableEntry[1024]; // L2 (Page Table)

        PageTable() {
            // Initialize entries to null (not present)
        }
    }

    static final class PageTableEntry {
        int ppn; // physical page number (<<12 to make PA)
        boolean V, R, W, X, U;
        boolean D; // Dirty bit (for Linux-like page management)
        boolean A; // Accessed bit (for page replacement)

        @Override
        public String toString() {
            return String.format("PTE{ppn=0x%x, V=%b, R=%b, W=%b, X=%b, D=%b, A=%b}",
                    ppn, V, R, W, X, D, A);
        }
    }

    // Helper methods for pager - Abstract interface for paging policies
    public int getPid() {
        return pid;
    }

    /**
     * Check if a page is present (resident)
     * Abstracts page table implementation from policies
     */
    public boolean isPagePresent(int vpn) {
        PageTableEntry pte = getPTEInternal(vpn);
        return pte != null && pte.V;
    }

    /**
     * Get the physical frame number for a page
     * Returns -1 if page not present
     */
    public int getFrameNumber(int vpn) {
        PageTableEntry pte = getPTEInternal(vpn);
        return (pte != null && pte.V) ? pte.ppn : -1;
    }

    /**
     * Map a page to a frame with specified permissions
     * Abstracts page table manipulation from policies
     */
    public boolean mapPage(int vpn, int frame, boolean write, boolean exec) {
        return mapPageInternal(vpn, frame, write, exec);
    }

    /**
     * Unmap a page (mark as not present)
     */
    public void unmapPage(int vpn) {
        PageTableEntry pte = getPTEInternal(vpn);
        if (pte != null) {
            pte.V = false;
        }
    }

    /**
     * Get page statistics for replacement policies
     * Returns reference bit (accessed) and dirty bit
     */
    public PageStats getPageStats(int vpn) {
        PageTableEntry pte = getPTEInternal(vpn);
        if (pte == null)
            return new PageStats(false, false);
        return new PageStats(pte.A, pte.D);
    }

    /**
     * Update page statistics after access
     */
    public void updatePageAccess(int vpn, boolean wasWrite) {
        PageTableEntry pte = getPTEInternal(vpn);
        if (pte != null) {
            pte.A = true;
            if (wasWrite)
                pte.D = true;
        }
    }

    public PageTableEntry getPTEInternal(int vpn) {
        int l1Index = (vpn >> 10) & 0x3FF;
        int l2Index = vpn & 0x3FF;

        PageTableEntry l1Entry = root.entries[l1Index];
        if (l1Entry == null || !l1Entry.V)
            return null;

        PageTable l2Table = getPageTable(l1Entry.ppn);
        if (l2Table == null)
            return null;

        return l2Table.entries[l2Index];
    }

    // Internal methods - hidden from policies
    private boolean mapPageInternal(int vpn, int frame, boolean write, boolean exec) {
        int l1Index = (vpn >> 10) & 0x3FF;
        int l2Index = vpn & 0x3FF;

        // Ensure L2 table exists
        PageTableEntry l1Entry = root.entries[l1Index];
        if (l1Entry == null || !l1Entry.V) {
            int l2TableFrame = allocatePageTable();
            if (l2TableFrame < 0)
                return false; // Out of memory

            l1Entry = new PageTableEntry();
            l1Entry.ppn = l2TableFrame;
            l1Entry.V = true;
            l1Entry.R = l1Entry.W = l1Entry.X = true;
            root.entries[l1Index] = l1Entry;
        }

        PageTable l2Table = getPageTable(l1Entry.ppn);
        if (l2Table == null)
            return false;

        PageTableEntry pte = new PageTableEntry();
        pte.ppn = frame;
        pte.V = true;
        pte.R = true;
        pte.W = write;
        pte.X = exec;
        pte.A = false;
        pte.D = false;

        l2Table.entries[l2Index] = pte;
        return true;
    }

    // Page statistics class for policies
    public static class PageStats {
        public final boolean accessed;
        public final boolean dirty;

        public PageStats(boolean accessed, boolean dirty) {
            this.accessed = accessed;
            this.dirty = dirty;
        }
    }

    public static int getVPN(int va) {
        return (va >> 12) & 0x3FFFFF; // 22-bit VPN for 4GB address space
    }

    public static int getPageOffset(int va) {
        return va & 0xFFF; // 12-bit page offset
    }

    // Page table management - now handled by PagedMemoryManager
    private final PagedMemoryManager memoryManager;

    AddressSpace(int pid, PagedMemoryManager memoryManager) {
        this.pid = pid;
        this.memoryManager = memoryManager;
        this.root = new PageDirectory();
    }

    private int allocatePageTable() {
        return memoryManager.allocatePageTableFrame();
    }

    public PageTable getPageTable(int ppn) {
        return memoryManager.getPageTableInstance(ppn);
    }

    // Address translation
    public int translateAddress(int virtualAddress) throws MemoryAccessException {
        int vpn = getVPN(virtualAddress);
        int offset = getPageOffset(virtualAddress);

        PageTableEntry pte = getPTEInternal(vpn);
        if (pte == null || !pte.V) {
            throw new MemoryAccessException("Page fault: VPN " + vpn + " not present");
        }

        return (pte.ppn << 12) | offset;
    }
}