package cse311.kernel.fs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SuperBlock {
    // xv6 layout: [ boot | sb | log | inodes | bit | data... ]
    public int size; // Size of file system (blocks)
    public int nblocks; // Number of data blocks
    public int ninodes; // Number of inodes.
    public int nlog; // Number of log blocks
    public int logstart; // Block number of first log block
    public int inodestart; // Block number of first inode block
    public int bmapstart; // Block number of first free map block

    // Read Superblock from disk (Block 1)
    public static SuperBlock read(DiskDevice disk) {
        byte[] buf = new byte[DiskDevice.BSIZE];
        disk.read(1, buf);
        ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);

        SuperBlock sb = new SuperBlock();
        sb.size = bb.getInt();
        sb.nblocks = bb.getInt();
        sb.ninodes = bb.getInt();
        sb.nlog = bb.getInt();
        sb.logstart = bb.getInt();
        sb.inodestart = bb.getInt();
        sb.bmapstart = bb.getInt();
        return sb;
    }
}
