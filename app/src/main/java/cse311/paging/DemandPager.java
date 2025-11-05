package cse311.paging;

import cse311.MemoryAccessException;

/**
 * Demand pager implementation that allocates pages on demand and supports eviction.
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
        if (PagedMemoryManager.isUart(va)) return -2; // MMIO
        
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
                mm.writeByte(frameStart + i, (byte) 0);
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
    
    // Helper method to find VPN for a given frame (simplified)
    private int findVpnForFrame(AddressSpace as, int frame) {
        // In a real implementation, this would use reverse mapping
        // For now, we'll use a simple approach
        return 0; // Placeholder - should be implemented properly
    }
}