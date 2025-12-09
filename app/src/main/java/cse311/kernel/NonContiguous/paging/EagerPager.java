package cse311.kernel.NonContiguous.paging;

import cse311.Exception.MemoryAccessException;

/**
 * Eager pager implementation that allocates pages immediately when needed.
 * This is similar to xv6's approach.
 */
public class EagerPager implements Pager {
    private final PagedMemoryManager mm;
    private final ReplacementPolicy repl;

    public EagerPager(PagedMemoryManager mm, ReplacementPolicy repl) {
        this.mm = mm;
        this.repl = repl;
    }

    @Override
    public int ensureResident(AddressSpace as, int va, VmAccess access) throws MemoryAccessException {
        if (PagedMemoryManager.isUart(va))
            return -2; // MMIO

        int vpn = AddressSpace.getVPN(va);

        if (!as.isPagePresent(vpn)) {
            // Need to allocate a new page
            int frame = mm.allocateFrame();
            if (frame < 0) {
                throw new MemoryAccessException("Out of physical memory");
            }

            // Map the page with default permissions (read/write/execute)
            boolean mapped = as.mapPage(vpn, frame, true, true);
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

        int frame = as.getFrameNumber(vpn);
        if (frame < 0) {
            throw new MemoryAccessException("Page not found after mapping");
        }

        as.updatePageAccess(vpn, false); // Update accessed bit
        repl.onAccess(frame);
        return frame;
    }
}