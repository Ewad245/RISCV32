package cse311.paging;

import cse311.MemoryAccessException;

/**
 * Demand pager implementation that allocates pages on demand and supports
 * eviction.
 */
public class DemandPager implements Pager {
    private final PagedMemoryManager mm;
    private final ReplacementPolicy repl;

    public DemandPager(PagedMemoryManager mm, ReplacementPolicy repl) {
        this.mm = mm;
        this.repl = repl;
    }

    @Override
    public int ensureResident(AddressSpace as, int va, VmAccess access) throws MemoryAccessException {
        if (PagedMemoryManager.isUart(va))
            return -2; // MMIO

        int vpn = AddressSpace.getVPN(va);

        if (!as.isPagePresent(vpn)) {
            // Page fault - need to allocate a frame
            int frame = mm.allocateFrame();
            if (frame < 0) {
                // Need to evict a page
                frame = repl.pickVictim(i -> true);
                if (frame >= 0) {
                    FrameOwner owner = mm.getFrameOwner(frame);
                    if (owner != null) {
                        // Find which VPN this frame belongs to and unmap it
                        int victimVpn = findVpnForFrame(as, frame);
                        if (victimVpn >= 0) {
                            as.unmapPage(victimVpn);
                        }
                        mm.setFrameOwner(frame, null);
                        repl.onUnmap(frame);
                    }
                    mm.freeFrame(frame);
                    frame = mm.allocateFrame();
                }
            }

            if (frame < 0) {
                throw new MemoryAccessException("Out of physical memory");
            }

            // Map the page with appropriate permissions
            boolean write = (access == VmAccess.WRITE);
            boolean exec = (access == VmAccess.EXEC);

            boolean mapped = as.mapPage(vpn, frame, write, exec);
            if (!mapped) {
                mm.freeFrame(frame);
                throw new MemoryAccessException("Failed to map page");
            }

            mm.setFrameOwner(frame, new FrameOwner(as.getPid(), vpn));

            // Zero the frame
            int frameStart = frame * PagedMemoryManager.PAGE_SIZE;
            for (int i = 0; i < PagedMemoryManager.PAGE_SIZE; i++) {
                mm.writeByteToPhysicalAddress(frameStart + i, (byte) 0);
            }

            repl.onMap(frame);
        }

        // Check permissions and update access bits
        AddressSpace.PageStats stats = as.getPageStats(vpn);
        int frame = as.getFrameNumber(vpn);

        if (frame < 0) {
            throw new MemoryAccessException("Page not found after mapping");
        }

        // Update access tracking
        as.updatePageAccess(vpn, access == VmAccess.WRITE);
        repl.onAccess(frame);

        return frame;
    }

    // Helper method to find VPN for a given frame by searching through page tables
    private int findVpnForFrame(AddressSpace as, int frame) {
        // Search through the 2-level page table structure
        for (int l1Index = 0; l1Index < 1024; l1Index++) {
            AddressSpace.PageTableEntry l1Entry = as.root.entries[l1Index];

            // Skip invalid L1 entries
            if (l1Entry == null || !l1Entry.V) {
                continue;
            }

            // Get the L2 page table
            AddressSpace.PageTable l2Table = as.getPageTable(l1Entry.ppn);
            if (l2Table == null) {
                continue;
            }

            // Search through L2 entries
            for (int l2Index = 0; l2Index < 1024; l2Index++) {
                AddressSpace.PageTableEntry pte = l2Table.entries[l2Index];

                // Check if this PTE is valid and maps to our target frame
                if (pte != null && pte.V && pte.ppn == frame) {
                    // Found it! Reconstruct the VPN
                    int vpn = (l1Index << 10) | l2Index;
                    return vpn;
                }
            }
        }

        // Not found
        return -1;
    }
}