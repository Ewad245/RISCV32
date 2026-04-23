package cse311.kernel.contiguous;

import java.util.List;

public class MemoryBlock {
    public int start;
    public int size;

    public MemoryBlock(int s, int sz) {
        start = s;
        size = sz;
    }
}
