package cse311.kernel.NonContiguous.paging;

import java.util.function.IntPredicate;

/**
 * Interface for page replacement policies.
 */
public interface ReplacementPolicy {
    /**
     * Called when a frame is accessed.
     */
    void onAccess(int frameIndex);

    /**
     * Called when a frame is mapped to a page.
     */
    void onMap(int frameIndex);

    /**
     * Called when a frame is unmapped from a page.
     */
    void onUnmap(int frameIndex);

    /**
     * Pick a victim frame for eviction.
     * 
     * @param canEvict predicate to check if a frame can be evicted
     * @return the frame index to evict, or -1 if no victim available
     */
    int pickVictim(IntPredicate canEvict);
}