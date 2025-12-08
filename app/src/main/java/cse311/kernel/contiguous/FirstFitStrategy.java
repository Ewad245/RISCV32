package cse311.kernel.contiguous;

import java.util.List;

public class FirstFitStrategy implements AllocationStrategy {
    @Override
    public int findRegion(List<MemoryBlock> holes, int size) {
        // Allocates the first hole that is large enough
        for (MemoryBlock hole : holes) {
            if (hole.size >= size)
                return hole.start;
        }
        return -1;
    }
}
