package cse311.pipeline;

/**
 * Configurable, Set-Associative L1 Cache Simulation Component.
 * Supports N-way set associativity, bitwise tag/index/offset decoding,
 * LRU line replacement, and hit/miss latency penalty calculation.
 */
public class L1Cache {

    public static class CacheLine {
        public boolean valid;
        public boolean dirty;
        public int tag;
        public long lastAccessCycle;

        public CacheLine() {
            this.valid = false;
            this.dirty = false;
            this.tag = 0;
            this.lastAccessCycle = 0;
        }
    }

    private final int numSets;
    private final int ways;
    private final int lineSizeBytes;
    private final int missPenalty;

    private final int offsetBits;
    private final int setBits;

    private final CacheLine[][] sets;
    private long totalHits;
    private long totalMisses;

    public L1Cache(int numSets, int ways, int lineSizeBytes, int missPenalty) {
        this.numSets = numSets;
        this.ways = ways;
        this.lineSizeBytes = lineSizeBytes;
        this.missPenalty = missPenalty;

        this.offsetBits = 31 - Integer.numberOfLeadingZeros(lineSizeBytes);
        this.setBits = 31 - Integer.numberOfLeadingZeros(numSets);

        this.sets = new CacheLine[numSets][ways];
        for (int i = 0; i < numSets; i++) {
            for (int j = 0; j < ways; j++) {
                this.sets[i][j] = new CacheLine();
            }
        }
        this.totalHits = 0;
        this.totalMisses = 0;
    }

    /**
     * Access cache at the given byte address.
     * 
     * @param address      Memory address
     * @param isWrite      True if write, False if read
     * @param currentCycle Current pipeline clock cycle
     * @return 0 if Cache Hit (1 cycle), missPenalty if Cache Miss (e.g. 10 cycles)
     */
    public int access(int address, boolean isWrite, long currentCycle) {
        int setIndex = (address >>> offsetBits) & (numSets - 1);
        int tag = address >>> (offsetBits + setBits);

        CacheLine[] set = sets[setIndex];

        // 1. Check for Hit
        for (int w = 0; w < ways; w++) {
            CacheLine line = set[w];
            if (line.valid && line.tag == tag) {
                line.lastAccessCycle = currentCycle;
                if (isWrite) {
                    line.dirty = true;
                }
                totalHits++;
                return 0; // Cache Hit (0 extra stall cycles)
            }
        }

        // 2. Cache Miss: Allocate LRU line
        allocateLruLine(set, tag, isWrite, currentCycle);
        totalMisses++;
        return missPenalty;
    }

    private void allocateLruLine(CacheLine[] set, int tag, boolean isWrite, long currentCycle) {
        int lruIndex = 0;
        long oldestAccess = Long.MAX_VALUE;

        for (int w = 0; w < ways; w++) {
            CacheLine line = set[w];
            if (!line.valid) {
                // Free line available immediately
                lruIndex = w;
                break;
            }
            if (line.lastAccessCycle < oldestAccess) {
                oldestAccess = line.lastAccessCycle;
                lruIndex = w;
            }
        }

        CacheLine victim = set[lruIndex];
        victim.valid = true;
        victim.dirty = isWrite;
        victim.tag = tag;
        victim.lastAccessCycle = currentCycle;
    }

    public double getHitRate() {
        long total = totalHits + totalMisses;
        return total == 0 ? 100.0 : (double) totalHits / total * 100.0;
    }

    public long getHits() {
        return totalHits;
    }

    public long getMisses() {
        return totalMisses;
    }

    public int getNumSets() {
        return numSets;
    }

    public int getWays() {
        return ways;
    }

    public int getLineSizeBytes() {
        return lineSizeBytes;
    }

    public int getMissPenalty() {
        return missPenalty;
    }

    public void reset() {
        for (int i = 0; i < numSets; i++) {
            for (int j = 0; j < ways; j++) {
                sets[i][j].valid = false;
                sets[i][j].dirty = false;
                sets[i][j].tag = 0;
                sets[i][j].lastAccessCycle = 0;
            }
        }
        totalHits = 0;
        totalMisses = 0;
    }
}
