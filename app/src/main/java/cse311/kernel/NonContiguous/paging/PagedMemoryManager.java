package cse311.kernel.NonContiguous.paging;

import cse311.MemoryManager;
import cse311.Exception.MemoryAccessException;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Sv32-like 2-level page table for per-process virtual memory.
 * 4 KiB pages, 1 GiB address space.
 * Refactored to use policy-based paging via Pager interface.
 */
public class PagedMemoryManager extends MemoryManager {
    public static final int PAGE_SIZE = 4096;
    private final BitSet freeFrames; // frame allocator
    private final int totalFrames;
    private final FrameOwner[] reverseMap; // reverse mapping for frame ownership

    // Page table management for 2-level structure
    private final Map<Integer, AddressSpace.PageTable> pageTableFrames = new HashMap<>();
    private final Map<Integer, AddressSpace.PageDirectory> pageDirectoryFrames = new HashMap<>();

    // Address-space mapping
    private final Map<Integer, AddressSpace> spaces = new HashMap<>();
    private AddressSpace current = null;
    private Pager pager = null; // Policy implementation

    // Physical UART mapping
    private static final int UART_BASE = 0x10000000;
    private static final int UART_SIZE = 0x1000;

    public static boolean isUart(int va) {
        return va >= UART_BASE && va < (UART_BASE + UART_SIZE);
    }

    public PagedMemoryManager(int totalMemoryBytes) {
        super(new cse311.SimpleMemory(totalMemoryBytes));
        this.totalFrames = totalMemoryBytes / PAGE_SIZE;
        this.freeFrames = new BitSet(totalFrames);
        this.reverseMap = new FrameOwner[totalFrames];
        this.freeFrames.set(0, totalFrames); // all free
    }

    /**
     * Set the pager policy implementation.
     */
    public void setPager(Pager pager) {
        this.pager = pager;
    }

    // ---- Address-space lifecycle ----
    public AddressSpace createAddressSpace(int pid) {
        AddressSpace as = new AddressSpace(pid, this);
        spaces.put(pid, as);
        return as;
    }

    public AddressSpace getAddressSpace(int pid) {
        return spaces.get(pid);
    }

    public void destroyAddressSpace(int pid) {
        AddressSpace as = spaces.get(pid);
        if (as == null)
            return;

        // 1. Iterate over the Page Directory (Level 1)
        for (int i = 0; i < 1024; i++) {
            AddressSpace.PageTableEntry l1Entry = as.root.entries[i];

            // If there is a valid L2 Page Table here
            if (l1Entry != null && l1Entry.V) {
                int l2Frame = l1Entry.ppn;
                AddressSpace.PageTable l2Table = getPageTableInstance(l2Frame);

                if (l2Table != null) {
                    // 2. Iterate over the Page Table (Level 2) to free DATA frames
                    for (int j = 0; j < 1024; j++) {
                        AddressSpace.PageTableEntry l2Entry = l2Table.entries[j];
                        if (l2Entry != null && l2Entry.V) {
                            // Free the actual data frame (User Memory)
                            freeFrame(l2Entry.ppn);
                        }
                    }
                }

                // 3. Free the frame that held the L2 Page Table itself
                freeFrame(l2Frame);
            }
        }

        // 4. Remove the logical structure
        spaces.remove(pid);
        System.out.println("PagedMemoryManager: Fully reclaimed memory for PID " + pid);
    }

    public void switchTo(AddressSpace as) {
        this.current = as;
    }

    // ---- Public helpers used by TaskManager/Kernel ----
    public void mapRegion(AddressSpace as, int va, int length, boolean R, boolean W, boolean X)
            throws MemoryAccessException {
        if ((va & (PAGE_SIZE - 1)) != 0)
            throw new MemoryAccessException("mapRegion: va not page-aligned");
        int pages = (length + PAGE_SIZE - 1) / PAGE_SIZE;
        for (int i = 0; i < pages; i++) {
            mapPage(as, va + i * PAGE_SIZE, R, W, X, /* zero-fill */true);
        }
    }

    // Policy-based memory access using Pager
    @Override
    public void writeByteToVirtualAddress(int va, byte val) throws MemoryAccessException {
        ensureCurrent();
        ensurePager();
        int frame = pager.ensureResident(current, va, VmAccess.WRITE);

        // --- FIX START ---
        if (frame == -2) { // UART MMIO
            // Delegate to parent MemoryManager to handle UART write
            super.writeByte(va, val);
            return;
        }
        // --- FIX END ---

        int pa = (frame << 12) | (va & 0xFFF);
        super.writeByte(pa, val);
    }

    @Override
    public byte readByte(int va) throws MemoryAccessException {
        ensureCurrent();
        ensurePager();
        int frame = pager.ensureResident(current, va, VmAccess.READ);

        // --- FIX START ---
        if (frame == -2) { // UART MMIO
            // Delegate to parent MemoryManager to handle UART read (Status/Data)
            return super.readByte(va);
        }
        // --- FIX END ---

        int pa = (frame << 12) | (va & 0xFFF);
        return super.readByte(pa);
    }

    @Override
    public short readHalfWord(int va) throws MemoryAccessException {
        ensureCurrent();
        ensurePager();
        int frame = pager.ensureResident(current, va, VmAccess.READ);

        // --- FIX START ---
        if (frame == -2)
            return super.readHalfWord(va);
        // --- FIX END ---

        int pa = (frame << 12) | (va & 0xFFF);
        return super.readHalfWord(pa);
    }

    @Override
    public int readWord(int va) throws MemoryAccessException {
        ensureCurrent();
        ensurePager();
        int frame = pager.ensureResident(current, va, VmAccess.READ);

        // --- FIX START ---
        if (frame == -2)
            return super.readWord(va);
        // --- FIX END ---

        int pa = (frame << 12) | (va & 0xFFF);
        return super.readWord(pa);
    }

    @Override
    public void writeByte(int va, byte v) throws MemoryAccessException {
        writeByteToVirtualAddress(va, v);
    }

    @Override
    public void writeHalfWord(int va, short v) throws MemoryAccessException {
        ensureCurrent();
        ensurePager();
        int frame = pager.ensureResident(current, va, VmAccess.WRITE);

        // --- FIX START ---
        if (frame == -2) {
            super.writeHalfWord(va, v);
            return;
        }
        // --- FIX END ---

        int pa = (frame << 12) | (va & 0xFFF);
        super.writeHalfWord(pa, v);
    }

    @Override
    public void writeWord(int va, int v) throws MemoryAccessException {
        ensureCurrent();
        ensurePager();
        int frame = pager.ensureResident(current, va, VmAccess.WRITE);

        // --- FIX START ---
        if (frame == -2) {
            super.writeWord(va, v);
            return;
        }
        // --- FIX END ---

        int pa = (frame << 12) | (va & 0xFFF);
        super.writeWord(pa, v);
    }

    // ---- Minimal UART passthrough (shared-mapped) ----
    @Override
    public void getInput(String data) {
        super.getInput(data);
    }

    @Override
    public String getMemoryMap() {
        return "PagedMemoryManager: 4KB pages, frames=" + totalFrames + ", spaces=" + spaces.size();
    }

    public int getTotalFrames() {
        return totalFrames;
    }

    // ---- Internals ----
    private void ensureCurrent() throws MemoryAccessException {
        if (current == null) {
            throw new MemoryAccessException("No current address space");
        }
    }

    private void ensurePager() throws MemoryAccessException {
        if (pager == null) {
            throw new MemoryAccessException("No pager policy configured");
        }
    }

    // Frame management methods for Pager implementations
    public int allocateFrame() {
        int frame = freeFrames.nextSetBit(0);
        if (frame != -1) {
            freeFrames.clear(frame);
            return frame;
        }
        return -1; // out of memory
    }

    public int allocateFrameForPageTable() {
        int frame = allocateFrame();
        if (frame != -1) {
            // Mark as page table frame
            setFrameOwner(frame, new FrameOwner(-1, -1)); // Special marker
        }
        return frame;
    }

    public void freeFrame(int frame) {
        if (frame >= 0 && frame < totalFrames) {
            freeFrames.set(frame);
            reverseMap[frame] = null;

            // Clean up page table mappings
            pageTableFrames.remove(frame);
            pageDirectoryFrames.remove(frame);
        }
    }

    public FrameOwner getFrameOwner(int frame) {
        return reverseMap[frame];
    }

    public void setFrameOwner(int frame, FrameOwner owner) {
        reverseMap[frame] = owner;
    }

    // ---- Debug helpers ----
    public void dumpStats() {
        int used = totalFrames - freeFrames.cardinality();
        System.out.println("Memory: " + used + "/" + totalFrames + " frames used");
        System.out.println("Page tables: " + pageTableFrames.size() + " allocated");
    }

    // Page table management methods
    public AddressSpace.PageTable getPageTableInstance(int ppn) {
        return pageTableFrames.get(ppn);
    }

    public AddressSpace.PageDirectory getPageDirectoryInstance(int ppn) {
        return pageDirectoryFrames.get(ppn);
    }

    public int allocatePageTableFrame() {
        int frame = allocateFrameForPageTable();
        if (frame != -1) {
            AddressSpace.PageTable table = new AddressSpace.PageTable();
            pageTableFrames.put(frame, table);
        }
        return frame;
    }

    private void mapPage(AddressSpace as, int va, boolean R, boolean W, boolean X, boolean zero)
            throws MemoryAccessException {
        ensurePager();

        int frame = pager.ensureResident(as, va, VmAccess.WRITE);
        int pa = frame * PAGE_SIZE;

        if (zero) {
            for (int i = 0; i < PAGE_SIZE; i++) {
                try {
                    super.writeByte(pa + i, (byte) 0);
                } catch (MemoryAccessException e) {
                    // Should not happen
                }
            }
        }

        // Set permissions
        int vpn = AddressSpace.getVPN(va);
        AddressSpace.PageTableEntry pte = as.getPTEInternal(vpn);
        if (pte != null) {
            pte.R = R;
            pte.W = W;
            pte.X = X;
        }
    }

    public void copyAddressSpace(AddressSpace oldAS, AddressSpace newAS) throws MemoryAccessException {
        System.out.println(
                "PagedMemoryManager: Copying address space from PID " + oldAS.getPid() + " to " + newAS.getPid());

        for (int l1Index = 0; l1Index < 1024; l1Index++) {
            AddressSpace.PageTableEntry l1Entry = oldAS.root.entries[l1Index];

            if (l1Entry == null || !l1Entry.V) {
                continue;
            }

            AddressSpace.PageTable l2Table = getPageTableInstance(l1Entry.ppn);
            if (l2Table == null) {
                continue;
            }

            for (int l2Index = 0; l2Index < 1024; l2Index++) {
                AddressSpace.PageTableEntry pte = l2Table.entries[l2Index];

                if (pte == null || !pte.V) {
                    continue;
                }

                int newFrame = allocateFrame();
                if (newFrame < 0) {
                    throw new MemoryAccessException("copyAddressSpace: out of physical memory");
                }

                int oldFrame = pte.ppn;
                int oldPa = oldFrame << 12;
                int newPa = newFrame << 12;

                try {
                    for (int i = 0; i < PAGE_SIZE; i++) {
                        byte b = super.readByte(oldPa + i);
                        super.writeByte(newPa + i, b);
                    }
                } catch (MemoryAccessException e) {
                    freeFrame(newFrame);
                    throw new MemoryAccessException("copyAddressSpace: failed to copy frame data: " + e.getMessage());
                }

                int vpn = (l1Index << 10) | l2Index;
                boolean mapped = newAS.mapPage(vpn, newFrame, pte.W, pte.X);

                if (!mapped) {
                    freeFrame(newFrame);
                    throw new MemoryAccessException("copyAddressSpace: mapPage failed for child");
                }

                setFrameOwner(newFrame, new FrameOwner(newAS.getPid(), vpn));
            }
        }
        System.out.println("PagedMemoryManager: Finished copying address space.");
    }

    public void writeByteToPhysicalAddress(int physicalAddress, byte value) throws MemoryAccessException {
        super.writeByte(physicalAddress, value);
    }
}