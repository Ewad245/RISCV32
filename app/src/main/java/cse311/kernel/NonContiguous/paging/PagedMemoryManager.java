package cse311.kernel.NonContiguous.paging;

import cse311.MemoryManager;
import cse311.Exception.MemoryAccessException;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    // Heat tracking for heatmap visualization
    private final int[] frameHeat; // 0..255, decays over time
    private static final int HEAT_MAX = 255;
    private static final int HEAT_INC = 6; // increment per access
    private static final int HEAT_DECAY = 2; // decrement per decay tick

    // Page table management for 2-level structure
    private final Map<Integer, AddressSpace.PageTable> pageTableFrames = new HashMap<>();
    private final Map<Integer, AddressSpace.PageDirectory> pageDirectoryFrames = new HashMap<>();

    // Address-space mapping
    private final Map<Integer, AddressSpace> spaces = new HashMap<>();

    // Per-Core (Thread) Active Address Space
    private final Map<Long, AddressSpace> activeContexts = new ConcurrentHashMap<>();

    private Pager pager = null; // Policy implementation

    // Shared Memory
    private Map<Integer, Integer> sharedKeyMap = new HashMap<>(); // Key (user provided) -> Frame Index
    private int[] frameRefCount;

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
        this.frameRefCount = new int[totalFrames];
        this.frameHeat = new int[totalFrames];
    }

    /**
     * Set the pager policy implementation.
     */
    public void setPager(Pager pager) {
        this.pager = pager;
    }

    // ---- Address-space lifecycle ----
    public synchronized AddressSpace createAddressSpace(int pid) {
        AddressSpace as = new AddressSpace(pid, this);
        spaces.put(pid, as);
        return as;
    }

    public AddressSpace getAddressSpace(int pid) {
        return spaces.get(pid);
    }

    /**
     * Register a child PID to an existing shared AddressSpace (for CLONE_VM).
     * This allows the child to be looked up by PID for context switching.
     */
    public synchronized void registerSharedAddressSpace(int childPid, AddressSpace as) {
        spaces.put(childPid, as);
    }

    public synchronized void destroyAddressSpace(int pid) {
        AddressSpace as = spaces.get(pid);
        if (as == null)
            return;

        // Decrement reference count (for CLONE_VM shared address spaces)
        // Only free memory when the last reference is released
        if (as.decrementRefCount() > 0) {
            // Other threads/processes still using this AddressSpace
            spaces.remove(pid);
            cse311.Logger.FileLogger.log(cse311.Logger.FileLogger.LogLevel.DEBUG,
                    "PagedMemoryManager: Detached PID " + pid + " from shared AddressSpace (refCount="
                            + as.getRefCount() + ")");
            return;
        }

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
        cse311.Logger.FileLogger.log(cse311.Logger.FileLogger.LogLevel.DEBUG,
                "PagedMemoryManager: Fully reclaimed memory for PID " + pid);
    }

    public void switchTo(AddressSpace as) {
        // Update the active address space for THIS thread (CPU)
        activeContexts.put(Thread.currentThread().getId(), as);
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
        if (isUart(va)) {
            super.writeByte(va, val);
            return;
        }
        AddressSpace current = ensureCurrent();
        ensurePager();
        int frame = pager.ensureResident(current, va, VmAccess.WRITE);

        int pa = (frame << 12) | (va & 0xFFF);
        bumpHeat(pa);
        super.writeByte(pa, val);
    }

    @Override
    public byte readByte(int va) throws MemoryAccessException {
        if (isUart(va)) {
            return super.readByte(va);
        }
        AddressSpace current = ensureCurrent();
        ensurePager();
        int frame = pager.ensureResident(current, va, VmAccess.READ);

        int pa = (frame << 12) | (va & 0xFFF);
        bumpHeat(pa);
        return super.readByte(pa);
    }

    @Override
    public short readHalfWord(int va) throws MemoryAccessException {
        if (isUart(va)) {
            return super.readHalfWord(va);
        }
        AddressSpace current = ensureCurrent();
        ensurePager();
        int frame = pager.ensureResident(current, va, VmAccess.READ);

        int pa = (frame << 12) | (va & 0xFFF);
        bumpHeat(pa);
        return super.readHalfWord(pa);
    }

    @Override
    public int readWord(int va) throws MemoryAccessException {
        if (isUart(va)) {
            return super.readWord(va);
        }
        AddressSpace current = ensureCurrent();
        ensurePager();
        int frame = pager.ensureResident(current, va, VmAccess.READ);

        int pa = (frame << 12) | (va & 0xFFF);
        bumpHeat(pa);
        return super.readWord(pa);
    }

    @Override
    public void writeByte(int va, byte v) throws MemoryAccessException {
        writeByteToVirtualAddress(va, v);
    }

    @Override
    public void writeHalfWord(int va, short v) throws MemoryAccessException {
        if (isUart(va)) {
            super.writeHalfWord(va, v);
            return;
        }
        AddressSpace current = ensureCurrent();
        ensurePager();
        int frame = pager.ensureResident(current, va, VmAccess.WRITE);

        int pa = (frame << 12) | (va & 0xFFF);
        bumpHeat(pa);
        super.writeHalfWord(pa, v);
    }

    @Override
    public void writeWord(int va, int v) throws MemoryAccessException {
        if (isUart(va)) {
            super.writeWord(va, v);
            return;
        }
        AddressSpace current = ensureCurrent();
        ensurePager();
        int frame = pager.ensureResident(current, va, VmAccess.WRITE);

        int pa = (frame << 12) | (va & 0xFFF);
        bumpHeat(pa);
        super.writeWord(pa, v);
    }

    @Override
    public synchronized int debugReadWord(int va, int pid) {
        if (isUart(va)) {
            return super.debugReadWord(va, pid); // UART is shared
        }

        // Find the AddressSpace for this PID
        AddressSpace as = spaces.get(pid);
        if (as == null) {
            return 0; // Or return super.readWord(va) if we assuming raw access? No, stick to context.
        }

        // We cannot use ensureResident here because it might modify state (allocate
        // pages)
        // and we don't want the debugger to cause page faults or allocations.
        // We only want to peek at what's already resident.

        int vpn = AddressSpace.getVPN(va);
        AddressSpace.PageTableEntry pte = as.getPTEInternal(vpn);

        if (pte != null && pte.V) {
            int frame = pte.ppn;
            int pa = (frame << 12) | (va & 0xFFF);
            try {
                return super.readWord(pa); // Read physical
            } catch (MemoryAccessException e) {
                return 0;
            }
        }

        return 0; // Page not present or not readable
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

    public FrameOwner[] getFrameOwners() {
        return reverseMap;
    }

    // ---- Internals ----
    // ---- Internals ----
    private AddressSpace ensureCurrent() throws MemoryAccessException {
        AddressSpace current = activeContexts.get(Thread.currentThread().getId());
        if (current == null) {
            throw new MemoryAccessException("No current address space");
        }
        return current;
    }

    private void ensurePager() throws MemoryAccessException {
        if (pager == null) {
            throw new MemoryAccessException("No pager policy configured");
        }
    }

    // Frame management methods for Pager implementations
    public synchronized int allocateFrame() {
        int frame = freeFrames.nextSetBit(0);
        if (frame != -1) {
            freeFrames.clear(frame);
            frameRefCount[frame] = 1; // Default to 1 owner
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

    public synchronized void freeFrame(int frame) {
        if (frame >= 0 && frame < totalFrames) {
            // Decrease reference count
            frameRefCount[frame]--;

            // Only free when no one is using it (refCount <= 0)
            if (frameRefCount[frame] <= 0) {
                freeFrames.set(frame);
                reverseMap[frame] = null;
                pageTableFrames.remove(frame);
                pageDirectoryFrames.remove(frame);

                // If frame is in shared map, remove it
                sharedKeyMap.values().removeIf(val -> val == frame);
            }
        }
    }

    public synchronized int openSharedRegion(int key) {
        // If key already exists, return old frame
        if (sharedKeyMap.containsKey(key)) {
            return sharedKeyMap.get(key);
        }

        // If not, create new frame
        int newFrame = allocateFrame();
        if (newFrame != -1) {
            sharedKeyMap.put(key, newFrame);
            // Zero-fill the new frame
            for (int i = 0; i < PAGE_SIZE; i++) {
                try {
                    writeByteToPhysicalAddress((newFrame << 12) + i, (byte) 0);
                } catch (Exception e) {
                }
            }
        }
        return newFrame;
    }

    // Manually map shared memory (set shared = true)
    public synchronized boolean mapSharedPage(AddressSpace as, int vpn, int frame, boolean write) {
        // Call internal mapPageInternal (assume you have access or modify visibility)
        // Or modify mapPage to accept shared parameter

        // Simplest way: Map normally then modify shared flag
        boolean success = as.mapPage(vpn, frame, write, false); // No Exec
        if (success) {
            // Increase refCount because another page table points to this frame
            frameRefCount[frame]++;

            // Set shared flag in PTE
            AddressSpace.PageTableEntry pte = as.getPTEInternal(vpn); // Need to expose this function or write logic to
                                                                      // find PTE
            if (pte != null)
                pte.shared = true;
        }
        return success;
    }

    public FrameOwner getFrameOwner(int frame) {
        return reverseMap[frame];
    }

    public void setFrameOwner(int frame, FrameOwner owner) {
        reverseMap[frame] = owner;
    }

    /**
     * Get the reference count for a frame.
     * Used by DemandPager to avoid evicting shared pages.
     */
    public int getFrameRefCount(int frame) {
        if (frame >= 0 && frame < totalFrames) {
            return frameRefCount[frame];
        }
        return 0;
    }

    // ---- Heat tracking for heatmap ----

    /**
     * Bump heat for the physical frame containing the given physical address.
     * Called after VA→PA translation to correctly track physical frame accesses.
     */
    private void bumpHeat(int pa) {
        int frame = pa >>> 12; // pa / PAGE_SIZE
        if (frame >= 0 && frame < totalFrames) {
            frameHeat[frame] = Math.min(HEAT_MAX, frameHeat[frame] + HEAT_INC);
        }
    }

    /**
     * Get the heat array for heatmap visualization.
     * Each element is 0..255 indicating recent access frequency.
     */
    public int[] getFrameHeat() {
        return frameHeat;
    }

    /**
     * Decay all frame heat values. Called periodically by the Kernel.
     * Prevents frames from staying permanently hot after a process exits.
     */
    public void decayHeat() {
        for (int i = 0; i < frameHeat.length; i++) {
            if (frameHeat[i] > 0) {
                frameHeat[i] = Math.max(0, frameHeat[i] - HEAT_DECAY);
            }
        }
    }

    // ---- Debug helpers ----
    public void dumpStats() {
        int used = totalFrames - freeFrames.cardinality();
        cse311.Logger.FileLogger.log("Memory: " + used + "/" + totalFrames + " frames used");
        cse311.Logger.FileLogger.log("Page tables: " + pageTableFrames.size() + " allocated");
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
        cse311.Logger.FileLogger.log(cse311.Logger.FileLogger.LogLevel.DEBUG,
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

            // Iterate over Page Table (Level 2)
            for (int l2Index = 0; l2Index < 1024; l2Index++) {
                AddressSpace.PageTableEntry pte = l2Table.entries[l2Index];

                if (pte == null || !pte.V) {
                    continue;
                }

                int vpn = (l1Index << 10) | l2Index;

                // Check if the child already has this VPN mapped
                // (e.g., from mapStack during allocateMemory) and free it before overwriting
                AddressSpace.PageTableEntry existingChildPTE = newAS.getPTEInternal(vpn);
                if (existingChildPTE != null && existingChildPTE.V) {
                    freeFrame(existingChildPTE.ppn);
                }

                int oldFrame = pte.ppn; // Physical frame of parent

                // --- CHECK: IF IT'S A SHARED PAGE ---
                if (pte.shared) {
                    // 1. Do not allocate new frame.
                    // 2. Map VPN of child directly to old frame of parent.
                    boolean mapped = newAS.mapPage(vpn, oldFrame, pte.W, pte.X);

                    if (mapped) {
                        // Mark PTE of child as shared
                        AddressSpace.PageTableEntry childPTE = newAS.getPTEInternal(vpn);
                        if (childPTE != null)
                            childPTE.shared = true;

                        // Increase reference count for this frame
                        frameRefCount[oldFrame]++;
                    } else {
                        throw new MemoryAccessException("copyAddressSpace: failed to map shared page");
                    }

                } else {
                    // --- CASE: NOT SHARED (PRIVATE) -> OLD LOGIC ---

                    // 1. Allocate new frame for child
                    int newFrame = allocateFrame();
                    if (newFrame < 0) {
                        throw new MemoryAccessException("copyAddressSpace: out of physical memory");
                    }

                    // 2. Calculate physical addresses to copy data
                    int oldPa = oldFrame << 12; // Source physical address
                    int newPa = newFrame << 12; // Destination physical address

                    if (l1Index == 0 && l2Index == 16) { // Code page usually at index 16 (0x10000)
                        int sampleData = super.readWord(oldPa);
                        cse311.Logger.FileLogger.log(cse311.Logger.FileLogger.LogLevel.DEBUG,
                                "copyAddressSpace: Copying Code Page. VPN=" + vpn + " OldFrame="
                                        + oldFrame
                                        + " OldPA=" + Integer.toHexString(oldPa) + " Data[0]="
                                        + Integer.toHexString(sampleData)
                                        + " NewFrame=" + newFrame);
                    }

                    try {
                        // Deep Copy each byte from parent to child
                        for (int i = 0; i < PAGE_SIZE; i++) {
                            byte b = super.readByte(oldPa + i);
                            super.writeByte(newPa + i, b);
                        }
                    } catch (MemoryAccessException e) {
                        freeFrame(newFrame);
                        throw new MemoryAccessException(
                                "copyAddressSpace: failed to copy frame data: " + e.getMessage());
                    }

                    // 3. Map frame mới vào bảng trang của con
                    boolean mapped = newAS.mapPage(vpn, newFrame, pte.W, pte.X);

                    if (!mapped) {
                        freeFrame(newFrame);
                        throw new MemoryAccessException("copyAddressSpace: mapPage failed for child");
                    }

                    setFrameOwner(newFrame, new FrameOwner(newAS.getPid(), vpn));
                }
            }
        }
        // System.out.println("PagedMemoryManager: Finished copying address space.");
    }

    public void writeByteToPhysicalAddress(int physicalAddress, byte value) throws MemoryAccessException {
        super.writeByte(physicalAddress, value);
    }

    public int unlinkSharedRegion(int key) {
        if (sharedKeyMap.containsKey(key)) {
            sharedKeyMap.remove(key); // Remove the key, the frame is still in use
            return 0; // Success
        }
        return -1; // Key not found
    }

    /**
     * Check if a frame contains only zeros (for UI visualization).
     * Samples bytes at intervals to avoid reading entire 4KB.
     * 
     * @param frameIndex The frame number to check
     * @return true if the frame appears empty (all sampled bytes are zero)
     */
    public boolean isFrameEmpty(int frameIndex) {
        if (frameIndex < 0 || frameIndex >= totalFrames) {
            return true;
        }

        int baseAddress = frameIndex * PAGE_SIZE;

        // Sample every 64 bytes (64 samples per 4KB frame) for performance
        try {
            for (int offset = 0; offset < PAGE_SIZE; offset += 64) {
                // Read a word (4 bytes) at each sample point
                int value = super.readWord(baseAddress + offset);
                if (value != 0) {
                    return false; // Found non-zero data
                }
            }
        } catch (Exception e) {
            return true; // On error, consider empty
        }

        return true; // All samples were zero
    }
}