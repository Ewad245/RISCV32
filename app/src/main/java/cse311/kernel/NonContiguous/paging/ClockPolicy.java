package cse311.kernel.NonContiguous.paging;

import java.util.BitSet;
import java.util.function.IntPredicate;

/**
 * Second-chance clock algorithm for page replacement.
 */
public class ClockPolicy implements ReplacementPolicy {
    private final BitSet referenced;
    private int hand = 0;
    private final int maxFrames;

    public ClockPolicy() {
        this(1024); // Default to 1024 frames
    }

    public ClockPolicy(int maxFrames) {
        this.maxFrames = maxFrames;
        this.referenced = new BitSet(maxFrames);
    }

    @Override
    public void onAccess(int frame) {
        if (frame >= 0 && frame < maxFrames) {
            referenced.set(frame);
        }
    }

    @Override
    public void onMap(int frame) {
        if (frame >= 0 && frame < maxFrames) {
            referenced.set(frame);
        }
    }

    @Override
    public void onUnmap(int frame) {
        if (frame >= 0 && frame < maxFrames) {
            referenced.clear(frame);
        }
    }

    @Override
    public int pickVictim(IntPredicate canEvict) {
        int start = hand;
        while (true) {
            if (hand >= maxFrames)
                hand = 0;

            if (!referenced.get(hand) && canEvict.test(hand)) {
                return hand;
            }

            referenced.clear(hand); // Give second chance
            hand = (hand + 1) % maxFrames;

            if (hand == start) {
                return -1; // No victim found
            }
        }
    }
}