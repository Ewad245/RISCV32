package cse311.kernel.contiguous;

import java.util.ArrayList;
import java.util.List;

import cse311.MemoryAccessException;
import cse311.kernel.memory.MemoryManagementUnit;

/**
 * Implements Contiguous Memory Allocation using Base/Limit registers.
 *
 */
public class ContiguousMMU implements MemoryManagementUnit {

    private final int totalMemory;
    private final AllocationStrategy allocator;

    // Simulates the Hardware Registers
    private int baseRegister = 0;
    private int limitRegister = 0;

    // Track free and allocated blocks
    private List<MemoryBlock> freeList = new ArrayList<>();
    private List<ProcessBlock> allocatedList = new ArrayList<>();

    public ContiguousMMU(int totalMemory, AllocationStrategy allocator) {
        this.totalMemory = totalMemory;
        this.allocator = allocator;
        // Initially one giant free block (hole)
        freeList.add(new MemoryBlock(0, totalMemory));
    }

    @Override
    public void switchContext(int pid) {
        // Load Base and Limit registers for the current process
        for (ProcessBlock pb : allocatedList) {
            if (pb.pid == pid) {
                this.baseRegister = pb.start;
                this.limitRegister = pb.size;
                return;
            }
        }
        // If kernel or not found
        this.baseRegister = 0;
        this.limitRegister = totalMemory;
    }

    @Override
    public int translate(int pid, int logicalAddr) throws MemoryAccessException {
        // Check Limit Register (Protection)
        if (logicalAddr >= limitRegister) {
            throw new MemoryAccessException("Segmentation Fault: logical address exceeds limit.");
        }
        // Apply Relocation Register
        return baseRegister + logicalAddr;
    }

    @Override
    public boolean allocateMemory(int pid, int size) {
        // Use strategy (First/Best/Worst fit) to find a hole
        int startAddr = allocator.findRegion(freeList, size);

        if (startAddr == -1) {
            // Check for External Fragmentation
            int totalFree = freeList.stream().mapToInt(b -> b.size).sum();
            if (totalFree >= size) {
                System.out.println("External Fragmentation detected. Compacting...");
                // External Fragmentation exists, try compaction
                compact();
                startAddr = allocator.findRegion(freeList, size);
            }
        }

        if (startAddr != -1) {
            // Allocate and adjust holes
            updateFreeList(startAddr, size);
            allocatedList.add(new ProcessBlock(pid, startAddr, size));
            return true;
        }
        return false;
    }

    @Override
    public void freeMemory(int pid) {
        allocatedList.removeIf(b -> {
            if (b.pid == pid) {
                // Return to free list
                freeList.add(new MemoryBlock(b.start, b.size));
                return true;
            }
            return false;
        });
        mergeHoles(); // Coalesce adjacent free blocks
    }

    @Override
    public void compact() {
        // Compaction shuffles memory to place all free memory in one large block.
        // Implementation: Move all allocated blocks to the start of memory.
        int currentPos = 0;
        for (ProcessBlock pb : allocatedList) {
            // Logic to move data in physical RAM would go here
            // System.arraycopy(...)
            pb.start = currentPos;
            currentPos += pb.size;
        }
        freeList.clear();
        if (currentPos < totalMemory) {
            freeList.add(new MemoryBlock(currentPos, totalMemory - currentPos));
        }
    }

    private void updateFreeList(int start, int size) {
        // Logic to split the hole used for allocation
        // This creates "Internal Fragmentation" if we allocate fixed partitions,
        // or just smaller holes in variable partitions
    }

    private void mergeHoles() {
        /* Logic to merge adjacent free blocks */
    }
}
