package cse311.example;

import java.util.List;
import java.util.Random;

import cse311.kernel.contiguous.AllocationStrategy;
import cse311.kernel.contiguous.MemoryBlock;

/**
 * Example SPI plugin allocator that picks a random hole that fits the request.
 */
public class RandomFitAllocator implements AllocationStrategy {

    private final Random random = new Random();

    @Override
    public int findRegion(List<MemoryBlock> holes, int requestSize) {
        if (holes == null || holes.isEmpty() || requestSize <= 0) {
            return -1;
        }

        // Collect all candidate holes
        int[] candidates = new int[holes.size()];
        int candidateCount = 0;

        for (int i = 0; i < holes.size(); i++) {
            MemoryBlock hole = holes.get(i);
            if (hole != null && hole.size >= requestSize) {
                candidates[candidateCount++] = i;
            }
        }

        if (candidateCount == 0) {
            return -1;
        }

        int chosenIndex = candidates[random.nextInt(candidateCount)];
        return holes.get(chosenIndex).start;
    }
}
