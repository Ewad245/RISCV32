package cse311;

import cse311.pipeline.L1Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class L1CacheTest {

    private L1Cache cache;

    @BeforeEach
    void setUp() {
        // 4 sets, 2 ways (2-way set associative), 32 bytes per line, 10-cycle miss penalty
        cache = new L1Cache(4, 2, 32, 10);
    }

    @Test
    void testInitialParameters() {
        assertAll("initialParameters",
            () -> assertEquals(4, cache.getNumSets(), "Number of sets should be 4"),
            () -> assertEquals(2, cache.getWays(), "Ways should be 2"),
            () -> assertEquals(32, cache.getLineSizeBytes(), "Line size should be 32 bytes"),
            () -> assertEquals(10, cache.getMissPenalty(), "Miss penalty should be 10"),
            () -> assertEquals(0, cache.getHits(), "Initial hits should be 0"),
            () -> assertEquals(0, cache.getMisses(), "Initial misses should be 0"),
            () -> assertEquals(100.0, cache.getHitRate(), "Initial hit rate should be 100.0")
        );
    }

    @Test
    void testColdMissLatency() {
        int addr = 0x1000;
        int penalty = cache.access(addr, false, 1);
        assertAll("coldMissState",
            () -> assertEquals(10, penalty, "Cold access must be a cache miss with penalty"),
            () -> assertEquals(0, cache.getHits(), "Hits should remain 0 after cold miss"),
            () -> assertEquals(1, cache.getMisses(), "Misses should be 1 after cold miss")
        );
    }

    @Test
    void testSubsequentHitLatency() {
        int addr = 0x1000;
        cache.access(addr, false, 1); // Cold miss

        int penalty = cache.access(addr, false, 2);
        assertAll("hitState",
            () -> assertEquals(0, penalty, "Subsequent access must be a cache hit with 0 penalty"),
            () -> assertEquals(1, cache.getHits(), "Hits should increment to 1"),
            () -> assertEquals(1, cache.getMisses(), "Misses should remain 1"),
            () -> assertEquals(50.0, cache.getHitRate(), 0.001, "Hit rate should be 50%")
        );
    }

    @Test
    void testCacheLineSpatialLocalityHit() {
        int addr = 0x1000;
        cache.access(addr, false, 1); // Cold miss

        // Access to another word within the same 32-byte cache line (offset +4) should hit
        int penalty = cache.access(addr + 4, false, 3);
        assertAll("spatialLocalityState",
            () -> assertEquals(0, penalty, "Access within same cache line should hit"),
            () -> assertEquals(1, cache.getHits(), "Hits should be 1"),
            () -> assertEquals(1, cache.getMisses(), "Misses should be 1")
        );
    }

    @Test
    void testLruEviction() {
        // Set size = 32 bytes (offsetBits = 5), numSets = 4 (setBits = 2)
        // Set 0 mapping: 0x000 (tag 0), 0x080 (tag 1), 0x100 (tag 2)
        cache.access(0x000, false, 10); // Fill Way 0 (cycle 10)
        cache.access(0x080, false, 20); // Fill Way 1 (cycle 20)

        // Access 0x000 again to make 0x080 the oldest (LRU) line
        cache.access(0x000, false, 30); // Hit (last access cycle 30)

        // Access 0x100: Set 0 is full, evict LRU line (0x080)
        int penalty = cache.access(0x100, false, 40);

        assertAll("lruEvictionState",
            () -> assertEquals(10, penalty, "Access to new line should miss and trigger eviction"),
            () -> assertEquals(0, cache.access(0x000, false, 50), "Recently accessed 0x000 must still hit"),
            () -> assertEquals(10, cache.access(0x080, false, 60), "Evicted line 0x080 must now miss")
        );
    }

    @Test
    void testWriteAndDirtyBitTracking() {
        int addr = 0x2000;

        int penalty = cache.access(addr, true, 100);
        int readPenalty = cache.access(addr, false, 105);

        assertAll("writeAndReadHitState",
            () -> assertEquals(10, penalty, "Write miss should incur penalty"),
            () -> assertEquals(0, readPenalty, "Read hit on previously written address should have 0 penalty"),
            () -> assertEquals(1, cache.getHits(), "Hits should be 1")
        );
    }

    @Test
    void testReset() {
        cache.access(0x1000, true, 10);
        cache.access(0x1000, false, 20);

        cache.reset();

        assertAll("resetState",
            () -> assertEquals(0, cache.getHits(), "Hits should be 0 after reset"),
            () -> assertEquals(0, cache.getMisses(), "Misses should be 0 after reset"),
            () -> assertEquals(100.0, cache.getHitRate(), "Hit rate should be 100.0 after reset"),
            () -> assertEquals(10, cache.access(0x1000, false, 30), "Access after reset must miss")
        );
    }
}
