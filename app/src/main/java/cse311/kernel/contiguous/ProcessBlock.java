package cse311.kernel.contiguous;

public class ProcessBlock {
    int pid;
    int start;
    int size;

    ProcessBlock(int p, int s, int sz) {
        pid = p;
        start = s;
        size = sz;
    }
}
