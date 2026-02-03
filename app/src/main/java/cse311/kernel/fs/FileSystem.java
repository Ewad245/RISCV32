package cse311.kernel.fs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class FileSystem {
    private DiskDevice disk;
    private SuperBlock sb;

    public FileSystem(String diskPath) {
        this.disk = new DiskDevice(diskPath);
        // Assuming disk is already formatted (mkfs)
        this.sb = SuperBlock.read(disk);
    }

    // --- Inode Operations ---

    public Inode getInode(int inum) {
        Inode ip = new Inode();
        ip.inum = inum;
        ip.ref = 1;

        // Calculate block number and offset
        int blockNum = sb.inodestart + (inum / Inode.IPB);
        int offset = (inum % Inode.IPB) * 64; // 64 bytes per inode

        byte[] buf = new byte[DiskDevice.BSIZE];
        disk.read(blockNum, buf);
        ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
        bb.position(offset);

        ip.type = bb.getShort();
        ip.major = bb.getShort();
        ip.minor = bb.getShort();
        ip.nlink = bb.getShort();
        ip.size = bb.getInt();
        for (int i = 0; i < Inode.NDIRECT + 1; i++) {
            ip.addrs[i] = bb.getInt();
        }

        return ip;
    }

    public void updateInode(Inode ip) {
        int blockNum = sb.inodestart + (ip.inum / Inode.IPB);
        int offset = (ip.inum % Inode.IPB) * 64;

        byte[] buf = new byte[DiskDevice.BSIZE];
        disk.read(blockNum, buf); // Read-Modify-Write

        ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
        bb.position(offset);

        bb.putShort(ip.type);
        bb.putShort(ip.major);
        bb.putShort(ip.minor);
        bb.putShort(ip.nlink);
        bb.putInt(ip.size);
        for (int i = 0; i < Inode.NDIRECT + 1; i++) {
            bb.putInt(ip.addrs[i]);
        }

        disk.write(blockNum, buf);
    }

    // --- Data Operations ---

    /**
     * Maps a logical file block index (0, 1, 2...) to a physical disk block
     */
    private int mapBlock(Inode ip, int logicalBlock) {
        if (logicalBlock < Inode.NDIRECT) {
            return ip.addrs[logicalBlock];
        }
        logicalBlock -= Inode.NDIRECT;

        if (logicalBlock < (DiskDevice.BSIZE / 4)) {
            int indirectBlock = ip.addrs[Inode.NDIRECT];
            if (indirectBlock == 0)
                return 0; // Hole

            byte[] buf = new byte[DiskDevice.BSIZE];
            disk.read(indirectBlock, buf);
            ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
            return bb.getInt(logicalBlock * 4);
        }
        throw new RuntimeException("File too large (Doubly indirect not implemented)");
    }

    public int readi(Inode ip, byte[] dst, int off, int n) {
        if (off > ip.size || n < 0)
            return 0;
        if (off + n > ip.size)
            n = ip.size - off;

        int tot = 0;
        while (tot < n) {
            int logicalBlock = (off + tot) / DiskDevice.BSIZE;
            int blockOff = (off + tot) % DiskDevice.BSIZE;
            int bytesToCopy = Math.min(n - tot, DiskDevice.BSIZE - blockOff);

            int physBlock = mapBlock(ip, logicalBlock);
            if (physBlock == 0) {
                // Reading a hole, just zero the buffer
                Arrays.fill(dst, tot, tot + bytesToCopy, (byte) 0);
            } else {
                byte[] buf = new byte[DiskDevice.BSIZE];
                disk.read(physBlock, buf);
                System.arraycopy(buf, blockOff, dst, tot, bytesToCopy);
            }
            tot += bytesToCopy;
        }
        return tot;
    }

    /**
     * Look for a directory entry in a directory inode.
     * 
     * @param dp   The directory inode
     * @param name The filename to search for
     * @return The inode of the found file, or null if not found
     */
    public Inode dirlookup(Inode dp, String name) {
        if (dp.type != Inode.T_DIR)
            throw new RuntimeException("dirlookup: not a directory");

        byte[] buf = new byte[DirectoryEntry.SIZE];
        for (int off = 0; off < dp.size; off += DirectoryEntry.SIZE) {
            if (readi(dp, buf, off, DirectoryEntry.SIZE) != DirectoryEntry.SIZE) {
                break;
            }
            DirectoryEntry de = DirectoryEntry.fromBytes(buf);
            if (de.inum == 0)
                continue; // Empty entry

            if (de.name.equals(name)) {
                // Found it! Return the inode.
                return getInode(de.inum);
            }
        }
        return null;
    }

    /**
     * Resolve a path (e.g., "/home/test") to an Inode.
     * Note: Simplification - does not handle parent directories ("..") complexly
     * yet.
     */
    public Inode namei(String path) {
        if (!path.startsWith("/"))
            return null; // Only absolute paths for now

        Inode ip = getInode(1); // Root Inode is always 1

        // Split path by '/' and iterate
        String[] parts = path.split("/");
        for (String part : parts) {
            if (part.isEmpty())
                continue; // Skip empty parts from "//"

            Inode next = dirlookup(ip, part);
            if (next == null) {
                return null; // Not found
            }
            ip = next;
        }
        return ip;
    }
}
