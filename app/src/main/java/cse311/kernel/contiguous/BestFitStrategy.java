package cse311.kernel.contiguous;

import java.util.List;

public class BestFitStrategy implements AllocationStrategy {
    @Override
    public int findRegion(List<MemoryBlock> holes, int size) {
        // Allocates the smallest hole that is sufficiently large
        MemoryBlock best = null;
        for (MemoryBlock hole : holes) {
            if (hole.size >= size) {
                if (best == null || hole.size < best.size)
                    best = hole;
            }
        }
        return (best != null) ? best.start : -1;
    }
}
