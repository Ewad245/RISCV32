package cse311.kernel.fs;

public class Inode {
    public static final short T_DIR = 1;
    public static final short T_FILE = 2;
    public static final short T_DEV = 3;

    public static final int NDIRECT = 11;
    public static final int IPB = DiskDevice.BSIZE / 64; // Inodes per block (sizeof(Inode) = 64)

    // On-disk data
    public short type;
    public short major;
    public short minor;
    public short nlink;
    public int size;
    public int[] addrs = new int[NDIRECT + 2]; // 11 direct, 1 indirect, 1 doubly-indirect (64 bytes total)

    // In-memory metadata
    public int inum;
    public int ref; // Reference count for open files

    public Inode() {
    }
}
