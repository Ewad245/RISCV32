package cse311.example;

import java.util.LinkedList;
import java.util.Queue;
import java.util.function.IntPredicate;

import com.google.auto.service.AutoService;
import cse311.kernel.NonContiguous.paging.ReplacementPolicy;

/**
 * Example SPI FIFO page replacement policy.
 */
@AutoService(ReplacementPolicy.class)
public class FifoReplacementPolicyExample implements ReplacementPolicy {

    private final Queue<Integer> frameQueue = new LinkedList<>();

    public FifoReplacementPolicyExample() {
        // Required SPI no-arg constructor
    }

    @Override
    public synchronized void onAccess(int frameIndex) {
        // FIFO ignores subsequent memory accesses
    }

    @Override
    public synchronized void onMap(int frameIndex) {
        if (!frameQueue.contains(frameIndex)) {
            frameQueue.offer(frameIndex);
        }
    }

    @Override
    public synchronized void onUnmap(int frameIndex) {
        frameQueue.remove(frameIndex);
    }

    @Override
    public synchronized int pickVictim(IntPredicate canEvict) {
        for (Integer frame : frameQueue) {
            if (frame != null && canEvict.test(frame)) {
                return frame;
            }
        }
        return -1;
    }
}

