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
    public Pipe pipe; // For FD_PIPE type

    public FileDescriptor(Inode inode, boolean readable, boolean writable) {
        this.type = FD_INODE;
        this.inode = inode;
        this.readable = readable;
        this.writable = writable;
        this.offset = 0;
        this.refCount = 1;
    }

    public FileDescriptor(Pipe pipe, boolean readable, boolean writable) {
        this.type = FD_PIPE;
        this.pipe = pipe;
        this.readable = readable;
        this.writable = writable;
        this.offset = 0;
        this.refCount = 1;
    }

    public void close() {
        // Only close the actual underlying resource if no one else is using it
        refCount--;
        if (refCount > 0) {
            return;
        }
        if (type == FD_INODE && inode != null) {
            inode.ref--;
        } else if (type == FD_PIPE && pipe != null) {
            if (readable)
                pipe.closeRead();
            if (writable)
                pipe.closeWrite();
        }
    }
}
