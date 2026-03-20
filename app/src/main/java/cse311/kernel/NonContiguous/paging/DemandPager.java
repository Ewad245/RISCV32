package cse311.kernel.NonContiguous.paging;

import cse311.Exception.MemoryAccessException;

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

            // Security check: reject accesses to invalid memory regions
            // (e.g. NULL pointer dereferences, gap between heap and stack)
            if (!as.isValidAccess(va)) {
                throw new MemoryAccessException(
                        "Segmentation Fault: Illegal access at 0x" + Integer.toHexString(va)
                                + " by PID " + as.getPid());
            }

            // Page fault - need to allocate a frame
            int frame = mm.allocateFrame();

            // If Out Of Memory (OOM), we must evict a victim frame
            if (frame < 0) {
                // 1. Pick a victim frame from ANY process (Global replacement)
                // FIX: Ensure we only evict User Data frames, NEVER Page Table frames!
                frame = repl.pickVictim(i -> {
                    if (mm.getFrameRefCount(i) != 1) return false;
                    FrameOwner owner = mm.getFrameOwner(i);
                    return owner != null && owner.pid != -1;
                });

                if (frame >= 0) {
                    // 2. Identify the owner of this victim frame
                    FrameOwner owner = mm.getFrameOwner(frame);

                    if (owner != null && owner.pid != -1) {
                        // 3. Retrieve the VICTIM'S address space (this fixes the cross-process
                        // corruption)
                        AddressSpace victimAS = mm.getAddressSpace(owner.pid);

                        if (victimAS != null) {
                            // 4. Unmap the page from the VICTIM'S page table
                            // We use the VPN stored in the FrameOwner record
                            victimAS.unmapPage(owner.vpn);
                        }

                        // 5. Clear ownership and notify the replacement policy
                        mm.setFrameOwner(frame, null);
                        repl.onUnmap(frame);
                    }

                    // 6. Mark the frame as free so we can immediately re-allocate it below
                    mm.freeFrame(frame);

                    // 7. Allocate the frame we just freed
                    frame = mm.allocateFrame();
                }
            }

            if (frame < 0) {
                throw new MemoryAccessException("Out of physical memory - Thrashing detected or Swap unavailable");
            }

            // Map the page with appropriate permissions
            boolean write = (access == VmAccess.WRITE);
            boolean exec = (access == VmAccess.EXEC);

            boolean mapped = as.mapPage(vpn, frame, write, exec);
            if (!mapped) {
                mm.freeFrame(frame);
                throw new MemoryAccessException("Failed to map page");
            }

            // Register new ownership for the current process
            mm.setFrameOwner(frame, new FrameOwner(as.getPid(), vpn));

            // Zero the frame (Security: prevent reading old data from previous process)
            int frameStart = frame * PagedMemoryManager.PAGE_SIZE;
            for (int i = 0; i < PagedMemoryManager.PAGE_SIZE; i++) {
                mm.writeByteToPhysicalAddress(frameStart + i, (byte) 0);
            }

            repl.onMap(frame);
        }

        // Check permissions and update access bits
        int frame = as.getFrameNumber(vpn);

        if (frame < 0) {
            throw new MemoryAccessException("Page not found after mapping");
        }

        // Enforce memory protection (PTE permission flags)
        AddressSpace.PageTableEntry pte = as.getPTEInternal(vpn);
        if (pte != null && pte.V) {
            if (access == VmAccess.WRITE && !pte.W) {
                throw new MemoryAccessException("Segmentation Fault: Write to Read-Only page at VPN " + vpn);
            }
            if (access == VmAccess.EXEC && !pte.X) {
                throw new MemoryAccessException("Segmentation Fault: Execute on Non-Executable page at VPN " + vpn);
            }
        }

        // Update access tracking
        as.updatePageAccess(vpn, access == VmAccess.WRITE);
        repl.onAccess(frame);

        return frame;
    }
}