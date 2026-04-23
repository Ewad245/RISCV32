package cse311.kernel.contiguous;

public class ProcessBlock {
    public int pid;
    public int start;
    public int size;

    public ProcessBlock(int p, int s, int sz) {
        pid = p;
        start = s;
        size = sz;
    }
}
