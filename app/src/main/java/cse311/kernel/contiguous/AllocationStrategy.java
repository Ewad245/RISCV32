package cse311.kernel.contiguous;

import java.util.List;

@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface AllocationStrategy {
    /**
     * Finds a memory region (hole) for the requested size.
     * 
     * @param holes       List of available free memory blocks.
     * @param requestSize Size needed.
     * @return The starting address of the block, or -1 if none found.
     */
    int findRegion(List<MemoryBlock> holes, int requestSize);
}
