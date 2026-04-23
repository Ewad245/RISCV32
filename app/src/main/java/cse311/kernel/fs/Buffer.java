package cse311.kernel.fs;

public class Buffer {
    public int blockNo;
    public final byte[] data = new byte[DiskDevice.BSIZE];
    
    public boolean valid = false;
    public boolean dirty = false;
    
    public int refCount = 0;
    public boolean locked = false;
    
    public Buffer prev;
    public Buffer next;
    
    public synchronized void acquire() throws InterruptedException {
        while (locked) {
            wait();
        }
        locked = true;
    }

    public synchronized void release() {
        locked = false;
        notifyAll();
    }
}
