package cse311.kernel.NonContiguous.paging;

/**
 * Tracks ownership of physical frames for reverse mapping.
 * Decoupled from page table implementation details.
 */
public class FrameOwner {
    public final int pid; // Process ID
    public final int vpn; // Virtual page number

    public FrameOwner(int pid, int vpn) {
        this.pid = pid;
        this.vpn = vpn;
    }
}