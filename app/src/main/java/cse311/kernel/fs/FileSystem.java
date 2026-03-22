package cse311.kernel.fs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import cse311.kernel.process.Task;

public class FileSystem {
    private DiskDevice disk;
    private SuperBlock sb;

    // Inode Cache for strict reference counting
    private static final int NINODE = 50;
    private final Inode[] inodeCache = new Inode[NINODE];
    private final Object inodeLock = new Object();

    public FileSystem(String diskPath) {
        this.disk = new DiskDevice(diskPath);
        // Assuming disk is already formatted (mkfs)
        this.sb = SuperBlock.read(disk);

        // Initialize the cache
        for (int i = 0; i < NINODE; i++) {
            inodeCache[i] = new Inode();
            inodeCache[i].ref = 0;
        }
    }

    // --- Inode Operations ---

    public Inode iget(int inum) {
        synchronized (inodeLock) {
            Inode empty = null;
            // 1. Find cached active inode
            for (Inode ip : inodeCache) {
                if (ip.ref > 0 && ip.inum == inum) {
                    ip.ref++;
                    return ip;
                }
                if (ip.ref == 0 && empty == null) {
                    empty = ip; // Remember a free slot just in case
                }
            }

            // 2. Allocate into an empty slot
            if (empty == null) {
                throw new RuntimeException("iget: no inodes available in cache");
            }

            empty.inum = inum;
            empty.ref = 1;

            // 3. Read from disk into the cached object
            int blockNum = sb.inodestart + (inum / Inode.IPB);
            int offset = (inum % Inode.IPB) * 64; // 64 bytes per inode

            byte[] buf = new byte[DiskDevice.BSIZE];
            disk.read(blockNum, buf);
            ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
            bb.position(offset);

            empty.type = bb.getShort();
            empty.major = bb.getShort();
            empty.minor = bb.getShort();
            empty.nlink = bb.getShort();
            empty.size = bb.getInt();
            for (int i = 0; i < Inode.NDIRECT + 1; i++) {
                empty.addrs[i] = bb.getInt();
            }

            return empty;
        }
    }

    public Inode idup(Inode ip) {
        synchronized (inodeLock) {
            ip.ref++;
            return ip;
        }
    }

    public void iput(Inode ip) {
        synchronized (inodeLock) {
            if (ip.ref == 1 && ip.nlink == 0 && ip.inum != 1) {
                // Last reference closed and file is unlinked (and not root). Free disk blocks!
                truncate(ip);
                ip.type = 0;
                updateInode(ip);
            }
            ip.ref--;
        }
    }

    // Allocate a new inode with the given type
    public Inode ialloc(short type) {
        for (int inum = 1; inum < sb.ninodes; inum++) {
            Inode ip = iget(inum);
            if (ip.type == 0) { // Free inode found
                ip.type = type;
                ip.size = 0;
                updateInode(ip);
                return ip;
            }
            iput(ip); // Not free, release it back to the cache
        }
        throw new RuntimeException("ialloc: out of inodes");
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

    // Allocate a free disk block
    private int balloc() {
        for (int b = 0; b < sb.size; b += DiskDevice.BSIZE * 8) {
            byte[] buf = new byte[DiskDevice.BSIZE];
            disk.read(sb.bmapstart + b / (DiskDevice.BSIZE * 8), buf);
            for (int bi = 0; bi < DiskDevice.BSIZE * 8 && b + bi < sb.size; bi++) {
                int m = 1 << (bi % 8);
                if ((buf[bi / 8] & m) == 0) {
                    buf[bi / 8] |= (byte) m; // Mark as used
                    disk.write(sb.bmapstart + b / (DiskDevice.BSIZE * 8), buf);
                    // Also zero the block
                    byte[] zeros = new byte[DiskDevice.BSIZE];
                    disk.write(b + bi, zeros);
                    return b + bi;
                }
            }
        }
        throw new RuntimeException("balloc: out of blocks");
    }

    /**
     * Maps a logical file block index (0, 1, 2...) to a physical disk block
     */
    private int mapBlock(Inode ip, int logicalBlock, boolean allocate) {
        if (logicalBlock < Inode.NDIRECT) {
            if (allocate && ip.addrs[logicalBlock] == 0) {
                ip.addrs[logicalBlock] = balloc();
            }
            return ip.addrs[logicalBlock];
        }
        logicalBlock -= Inode.NDIRECT;

        if (logicalBlock < (DiskDevice.BSIZE / 4)) {
            int indirectBlock = ip.addrs[Inode.NDIRECT];
            if (allocate && indirectBlock == 0) {
                indirectBlock = balloc();
                ip.addrs[Inode.NDIRECT] = indirectBlock;
            }
            if (indirectBlock == 0)
                return 0; // Hole

            byte[] buf = new byte[DiskDevice.BSIZE];
            disk.read(indirectBlock, buf);
            ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);

            int physBlock = bb.getInt(logicalBlock * 4);
            if (allocate && physBlock == 0) {
                physBlock = balloc();
                bb.putInt(logicalBlock * 4, physBlock);
                disk.write(indirectBlock, buf);
            }
            return physBlock;
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

            int physBlock = mapBlock(ip, logicalBlock, false);
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

    public int writei(Inode ip, byte[] src, int off, int n) {
        if (off > ip.size || off < 0)
            return -1;

        // Simplified max size check
        if (off + n > (Inode.NDIRECT + DiskDevice.BSIZE / 4) * DiskDevice.BSIZE) {
            n = ((Inode.NDIRECT + DiskDevice.BSIZE / 4) * DiskDevice.BSIZE) - off;
        }

        int tot = 0;
        while (tot < n) {
            int logicalBlock = (off + tot) / DiskDevice.BSIZE;
            int blockOff = (off + tot) % DiskDevice.BSIZE;
            int bytesToCopy = Math.min(n - tot, DiskDevice.BSIZE - blockOff);

            int physBlock = mapBlock(ip, logicalBlock, true);
            if (physBlock == 0)
                break; // Disk full

            byte[] buf = new byte[DiskDevice.BSIZE];
            if (bytesToCopy < DiskDevice.BSIZE) {
                disk.read(physBlock, buf); // Read-modify-write
            }
            System.arraycopy(src, tot, buf, blockOff, bytesToCopy);
            disk.write(physBlock, buf);
            tot += bytesToCopy;
        }

        if (off + tot > ip.size) {
            ip.size = off + tot;
            updateInode(ip);
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
                // Found it! Return the newly cached instance
                return iget(de.inum);
            }
        }
        return null;
    }

    public int dirlink(Inode dp, String name, int inum) {
        // Check if name already exists
        if (dirlookup(dp, name) != null)
            return -1;

        // Look for an empty DirectoryEntry (inum == 0)
        byte[] buf = new byte[DirectoryEntry.SIZE];
        for (int off = 0; off < dp.size; off += DirectoryEntry.SIZE) {
            readi(dp, buf, off, DirectoryEntry.SIZE);
            DirectoryEntry de = DirectoryEntry.fromBytes(buf);
            if (de.inum == 0) {
                de.inum = inum;
                de.name = name;
                writei(dp, de.toBytes(), off, DirectoryEntry.SIZE);
                return 0;
            }
        }

        // No empty slot found, append a new one
        DirectoryEntry newEntry = new DirectoryEntry(inum, name);
        if (writei(dp, newEntry.toBytes(), dp.size, DirectoryEntry.SIZE) != DirectoryEntry.SIZE) {
            return -1;
        }
        return 0;
    }

    /**
     * Resolve a path (e.g., "/home/test") to an Inode.
     * Starts from root if path is absolute, otherwise from task's cwd.
     */
    public Inode namei(Task task, String path) {
        if (path == null || path.isEmpty())
            return null;
        Inode ip;
        if (path.startsWith("/")) {
            ip = iget(1); // Root
        } else {
            ip = (task != null && task.cwd != null) ? idup(task.cwd) : iget(1);
        }

        String[] parts = path.split("/");
        for (String part : parts) {
            if (part.isEmpty() || part.equals("."))
                continue;
            Inode next = dirlookup(ip, part);
            iput(ip); // Release the parent before moving down

            if (next == null)
                return null;
            ip = next;
        }
        return ip;
    }

    public Inode namei(String path) {
        return namei(null, path);
    }

    /**
     * Resolves the parent directory of a path, and returns the final path component
     * in the provided StringBuilder.
     */
    public Inode nameiparent(Task task, String path, StringBuilder name) {
        if (path == null || path.isEmpty())
            return null;
        Inode ip;
        if (path.startsWith("/")) {
            ip = iget(1); // Root
        } else {
            ip = (task != null && task.cwd != null) ? idup(task.cwd) : iget(1);
        }

        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.equals("."))
                continue;
            Inode next = dirlookup(ip, part);
            iput(ip); // Release the parent before moving down
            if (next == null)
                return null;
            ip = next;
        }

        if (parts.length > 0) {
            name.append(parts[parts.length - 1]);
        }
        return ip;
    }

    public void truncate(Inode ip) {
        if (ip.type == Inode.T_DEV)
            return; // Don't free device "blocks"

        // 1. Free direct blocks
        for (int i = 0; i < Inode.NDIRECT; i++) {
            if (ip.addrs[i] != 0) {
                bfree(ip.addrs[i]);
                ip.addrs[i] = 0;
            }
        }

        // 2. Free indirect blocks
        if (ip.addrs[Inode.NDIRECT] != 0) { // 1024-byte block / 4 bytes per int = 256
            byte[] indirectData = new byte[DiskDevice.BSIZE];
            disk.read(ip.addrs[Inode.NDIRECT], indirectData);
            ByteBuffer buf = ByteBuffer.wrap(indirectData);
            buf.order(ByteOrder.LITTLE_ENDIAN);

            for (int i = 0; i < 256; i++) {
                int blockNum = buf.getInt();
                if (blockNum != 0) {
                    bfree(blockNum);
                }
            }

            // Free the indirect block itself
            bfree(ip.addrs[Inode.NDIRECT]);
            ip.addrs[Inode.NDIRECT] = 0;
        }

        ip.size = 0;
        updateInode(ip); // Save the cleared inode back to disk
    }

    private void bfree(int blockNum) {
        byte[] bitmap = new byte[DiskDevice.BSIZE];
        int bitmapBlock = sb.bmapstart + blockNum / (DiskDevice.BSIZE * 8);
        disk.read(bitmapBlock, bitmap);

        int bitIndex = blockNum % (DiskDevice.BSIZE * 8);
        bitmap[bitIndex / 8] &= (byte) ~(1 << (bitIndex % 8));

        disk.write(bitmapBlock, bitmap);

        byte[] zeros = new byte[DiskDevice.BSIZE];
        disk.write(blockNum, zeros);
    }
}
