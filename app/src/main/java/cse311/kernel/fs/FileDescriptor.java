package cse311.kernel.fs;

public class FileDescriptor {
    public static final int FD_NONE = 0;
    public static final int FD_PIPE = 1;
    public static final int FD_INODE = 2;
    public static final int FD_DEVICE = 3;

    public int type;
    public int refCount;
    public boolean readable;
    public boolean writable;

    public Inode inode; // The underlying file
    public int offset; // Current read/write position

    public FileDescriptor(Inode inode, boolean readable, boolean writable) {
        this.type = FD_INODE;
        this.inode = inode;
        this.readable = readable;
        this.writable = writable;
        this.offset = 0;
        this.refCount = 1;
    }

    public void close() {
        // In a real OS, we would decrement Inode ref count here
        // inode.ref--;
    }
}
