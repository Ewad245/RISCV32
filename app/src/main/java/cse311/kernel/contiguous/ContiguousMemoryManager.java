package cse311.kernel.contiguous;

import java.util.ArrayList;
import java.util.List;

import cse311.MemoryManager;
import cse311.SimpleMemory;
import cse311.Exception.MemoryAccessException;

/**
 * Implements Contiguous Memory Allocation using Base/Limit registers.
 * Acts as the MemoryManager for the CPU in Contiguous Mode.
 */
public class ContiguousMemoryManager extends MemoryManager {

    private final int totalMemory;
    private final AllocationStrategy allocator;

    // Simulates the Hardware Registers
    private int baseRegister = 0;
    private int limitRegister = 0;
    private int currentPid = -1;

    // Track free and allocated blocks
    private List<MemoryBlock> freeList = new ArrayList<>();
    private List<ProcessBlock> allocatedList = new ArrayList<>();

    public ContiguousMemoryManager(int totalMemory, AllocationStrategy allocator) {
        // Initialize the underlying physical RAM
        super(new SimpleMemory(totalMemory));
        this.totalMemory = totalMemory;
        this.allocator = allocator;
        // Initially one giant free block (hole)
        freeList.add(new MemoryBlock(0, totalMemory));
    }

    public int getLimitRegister() {
        return this.limitRegister;
    }

    private boolean isMMIO(int address) {
        return address >= UART_BASE && address < (UART_BASE + UART_SIZE);
    }

    /**
     * Switch hardware context (Base/Limit registers) to a specific process.
     */
    public void switchContext(int pid) {
        this.currentPid = pid;
        // Load Base and Limit registers for the current process
        for (ProcessBlock pb : allocatedList) {
            if (pb.pid == pid) {
                this.baseRegister = pb.start;
                this.limitRegister = pb.size;
                return;
            }
        }
        // If kernel or not found, grant full access (or default to 0)
        this.baseRegister = 0;
        this.limitRegister = totalMemory;
    }

    /**
     * Translate Logical Address -> Physical Address
     */
    public int translate(int logicalAddr) throws MemoryAccessException {
        // Check Limit Register (Protection)
        if (logicalAddr >= limitRegister) {
            throw new MemoryAccessException(
                    String.format("Segmentation Fault: PID %d accessed 0x%08X (Limit: 0x%08X)",
                            currentPid, logicalAddr, limitRegister));
        }
        // Apply Relocation Register
        return baseRegister + logicalAddr;
    }

    // --- OVERRIDE MEMORY ACCESS METHODS ---
    // These intercept CPU requests and apply translation logic

    @Override
    public byte readByte(int va) throws MemoryAccessException {
        // Allow direct access to UART without translation/limit check
        if (isMMIO(va)) {
            return super.readByte(va); // Pass directly to SimpleMemory (which handles UART)
        }
        // 1. Translate
        int pa = translate(va);
        // 2. Access Physical RAM (via parent)
        return super.readByte(pa);
    }

    @Override
    public void writeByte(int va, byte value) throws MemoryAccessException {
        if (isMMIO(va)) {
            super.writeByte(va, value);
            return;
        }
        int pa = translate(va);
        super.writeByte(pa, value);
    }

    @Override
    public int readWord(int va) throws MemoryAccessException {
        if (isMMIO(va)) {
            return super.readWord(va);
        }
        int pa = translate(va);
        // Note: super.readWord will check physical alignment
        return super.readWord(pa);
    }

    @Override
    public void writeWord(int va, int value) throws MemoryAccessException {
        if (isMMIO(va)) {
            super.writeWord(va, value);
            return;
        }
        int pa = translate(va);
        super.writeWord(pa, value);
    }

    @Override
    public short readHalfWord(int va) throws MemoryAccessException {
        int pa = translate(va);
        return super.readHalfWord(pa);
    }

    @Override
    public void writeHalfWord(int va, short value) throws MemoryAccessException {
        int pa = translate(va);
        super.writeHalfWord(pa, value);
    }

    @Override
    public void writeByteToVirtualAddress(int va, byte value) throws MemoryAccessException {
        // Used by ElfLoader. It writes to "Logical Address".
        // We simply delegate to our translation logic.
        this.writeByte(va, value);
    }

    // --- ALLOCATION LOGIC (Managed by Coordinator) ---

    public boolean allocateMemory(int pid, int size) {
        // Use strategy (First/Best/Worst fit) to find a hole
        int startAddr = allocator.findRegion(freeList, size);

        if (startAddr == -1) {
            // Check for External Fragmentation
            int totalFree = freeList.stream().mapToInt(b -> b.size).sum();
            if (totalFree >= size) {
                System.out.println("External Fragmentation detected. Compacting...");
                compact();
                startAddr = allocator.findRegion(freeList, size);
            }
        }

        if (startAddr != -1) {
            updateFreeList(startAddr, size);
            allocatedList.add(new ProcessBlock(pid, startAddr, size));
            return true;
        }
        return false;
    }

    /**
     * Copies the content of the parent's memory partition to the child's partition.
     * Essential for fork() implementation.
     */
    public boolean copyMemory(int parentPid, int childPid) {
        ProcessBlock parent = null;
        ProcessBlock child = null;

        // 1. Find the memory blocks for both processes
        for (ProcessBlock pb : allocatedList) {
            if (pb.pid == parentPid)
                parent = pb;
            if (pb.pid == childPid)
                child = pb;
        }

        if (parent == null || child == null) {
            System.err.println("Contiguous Copy Failed: PIDs not found (P:" + parentPid + ", C:" + childPid + ")");
            return false;
        }

        // 2. Access the physical RAM
        byte[] ram = this.getByteMemory();

        // 3. Determine safe copy size (prevent overflow if child < parent)
        // Note: Ideally child.size >= parent.size, but we use min() for safety.
        int bytesToCopy = Math.min(parent.size, child.size);

        // 4. Perform the physical copy
        try {
            System.arraycopy(ram, parent.start, ram, child.start, bytesToCopy);
            return true;
        } catch (Exception e) {
            System.err.println("Contiguous Copy Error: " + e.getMessage());
            return false;
        }
    }

    public void freeMemory(int pid) {
        allocatedList.removeIf(b -> {
            if (b.pid == pid) {
                freeList.add(new MemoryBlock(b.start, b.size));
                return true;
            }
            return false;
        });
        mergeHoles();
    }

    public void compact() {
        // Simple compaction: Move all allocated blocks to the start
        int currentPos = 0;

        // We need to move the actual bytes in physical memory!
        byte[] ram = this.getByteMemory(); // Access raw array from parent

        for (ProcessBlock pb : allocatedList) {
            if (pb.start != currentPos) {
                // Move data: copy pb.size bytes from pb.start to currentPos
                System.arraycopy(ram, pb.start, ram, currentPos, pb.size);
                pb.start = currentPos;
            }
            currentPos += pb.size;
        }

        // Update free list: One big hole at the end
        freeList.clear();
        if (currentPos < totalMemory) {
            freeList.add(new MemoryBlock(currentPos, totalMemory - currentPos));
        }
    }

    private void updateFreeList(int start, int size) {
        // Split the block used
        for (int i = 0; i < freeList.size(); i++) {
            MemoryBlock block = freeList.get(i);
            if (block.start == start) {
                if (block.size == size) {
                    freeList.remove(i); // Exact fit
                } else {
                    // Update block to remaining space
                    block.start += size;
                    block.size -= size;
                }
                break;
            }
        }
    }

    private void mergeHoles() {
        // Sort by address
        freeList.sort((a, b) -> Integer.compare(a.start, b.start));

        // Merge adjacent
        for (int i = 0; i < freeList.size() - 1; i++) {
            MemoryBlock current = freeList.get(i);
            MemoryBlock next = freeList.get(i + 1);

            if (current.start + current.size == next.start) {
                current.size += next.size;
                freeList.remove(i + 1);
                i--; // Retry this index
            }
        }
    }
}