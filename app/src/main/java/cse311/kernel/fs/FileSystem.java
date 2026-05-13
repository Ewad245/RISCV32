package cse311.kernel.fs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import cse311.kernel.Kernel;
import cse311.kernel.process.Task;

@SuppressWarnings({
    "PMD.AvoidCatchingGenericException",
    "PMD.AvoidReassigningParameters",
    "PMD.LiteralsFirstInComparisons"
})
public class FileSystem {
    private DiskDevice disk;
    private BufferCache bcache;
    private SuperBlock sb;
    public Log log; // Transaction log for journaling

    // Inode Cache for strict reference counting
    private static final int NINODE = 50;
    private final Inode[] inodeCache = new Inode[NINODE];
    private final Object inodeLock = new Object();

    public FileSystem(String diskPath, Kernel kernel) {
        this.disk = new DiskDevice(diskPath);
        this.bcache = new BufferCache(disk);
        // Assuming disk is already formatted (mkfs)
        this.sb = SuperBlock.read(disk);

        // Initialize the cache
        for (int i = 0; i < NINODE; i++) {
            inodeCache[i] = new Inode();
            inodeCache[i].ref = 0;
        }

        // Initialize transaction log for journaling
        this.log = new Log(kernel, this.bcache, sb.logstart, sb.nlog);
    }

    public DiskDevice getDiskDevice() {
        return disk;
    }

    public BufferCache getBufferCache() {
        return bcache;
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

            try {
                Buffer b = bcache.bread(blockNum);
                try {
                    ByteBuffer bb = ByteBuffer.wrap(b.data).order(ByteOrder.LITTLE_ENDIAN);
                    bb.position(offset);

                    empty.type = bb.getShort();
                    empty.major = bb.getShort();
                    empty.minor = bb.getShort();
                    empty.nlink = bb.getShort();
                    empty.size = bb.getInt();
                    for (int i = 0; i < Inode.NDIRECT + 1; i++) {
                        empty.addrs[i] = bb.getInt();
                    }
                } finally {
                    bcache.brelse(b);
                }
            } catch (Exception e) {
                throw new RuntimeException("iget: failed to read inode block", e);
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

        try {
            Buffer b = bcache.bread(blockNum);
            try {
                ByteBuffer bb = ByteBuffer.wrap(b.data).order(ByteOrder.LITTLE_ENDIAN);
                bb.position(offset);

                bb.putShort(ip.type);
                bb.putShort(ip.major);
                bb.putShort(ip.minor);
                bb.putShort(ip.nlink);
                bb.putInt(ip.size);
                for (int i = 0; i < Inode.NDIRECT + 1; i++) {
                    bb.putInt(ip.addrs[i]);
                }
                b.dirty = true;
                log.write(b);
            } finally {
                bcache.brelse(b);
            }
        } catch (Exception e) {
            throw new RuntimeException("updateInode: failed to write inode block", e);
        }
    }

    // --- Data Operations ---

    // Allocate a free disk block
    private int balloc() {
        for (int b = 0; b < sb.size; b += DiskDevice.BSIZE * 8) {
            int bitmapBlockNum = sb.bmapstart + b / (DiskDevice.BSIZE * 8);
            try {
                Buffer bbuf = bcache.bread(bitmapBlockNum);
                try {
                    for (int bi = 0; bi < DiskDevice.BSIZE * 8 && b + bi < sb.size; bi++) {
                        int m = 1 << (bi % 8);
                        if ((bbuf.data[bi / 8] & m) == 0) {
                            bbuf.data[bi / 8] |= (byte) m; // Mark as used
                            bbuf.dirty = true;
                            log.write(bbuf);

                            // Zero the allocated block
                            Buffer newBlock = bcache.bread(b + bi);
                            try {
                                Arrays.fill(newBlock.data, (byte) 0);
                                newBlock.dirty = true;
                                log.write(newBlock);
                            } finally {
                                bcache.brelse(newBlock);
                            }

                            return b + bi;
                        }
                    }
                } finally {
                    bcache.brelse(bbuf);
                }
            } catch (Exception e) {
                throw new RuntimeException("balloc: failed to read bitmap block", e);
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

            try {
                Buffer b = bcache.bread(indirectBlock);
                try {
                    ByteBuffer bb = ByteBuffer.wrap(b.data).order(ByteOrder.LITTLE_ENDIAN);
                    int physBlock = bb.getInt(logicalBlock * 4);
                    if (allocate && physBlock == 0) {
                        physBlock = balloc();
                        bb.putInt(logicalBlock * 4, physBlock);
                        b.dirty = true;
                        log.write(b);
                    }
                    return physBlock;
                } finally {
                    bcache.brelse(b);
                }
            } catch (Exception e) {
                throw new RuntimeException("mapBlock: failed to read indirect block", e);
            }
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
                try {
                    Buffer b = bcache.bread(physBlock);
                    try {
                        System.arraycopy(b.data, blockOff, dst, tot, bytesToCopy);
                    } finally {
                        bcache.brelse(b);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("readi: failed to read data block", e);
                }
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

            try {
                Buffer b = bcache.bread(physBlock);
                try {
                    System.arraycopy(src, tot, b.data, blockOff, bytesToCopy);
                    b.dirty = true;
                    log.write(b);
                } finally {
                    bcache.brelse(b);
                }
            } catch (Exception e) {
                throw new RuntimeException("writei: failed to write data block", e);
            }
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
        if (ip.addrs[Inode.NDIRECT] != 0) {
            try {
                Buffer b = bcache.bread(ip.addrs[Inode.NDIRECT]);
                try {
                    ByteBuffer buf = ByteBuffer.wrap(b.data);
                    buf.order(ByteOrder.LITTLE_ENDIAN);

                    for (int i = 0; i < 256; i++) {
                        int blockNum = buf.getInt();
                        if (blockNum != 0) {
                            bfree(blockNum);
                        }
                    }
                } finally {
                    bcache.brelse(b);
                }
            } catch (Exception e) {
                throw new RuntimeException("truncate: failed to read indirect block", e);
            }

            // Free the indirect block itself
            bfree(ip.addrs[Inode.NDIRECT]);
            ip.addrs[Inode.NDIRECT] = 0;
        }

        ip.size = 0;
        updateInode(ip);
    }

    private void bfree(int blockNum) {
        int bitmapBlock = sb.bmapstart + blockNum / (DiskDevice.BSIZE * 8);
        try {
            Buffer bbuf = bcache.bread(bitmapBlock);
            try {
                int bitIndex = blockNum % (DiskDevice.BSIZE * 8);
                bbuf.data[bitIndex / 8] &= (byte) ~(1 << (bitIndex % 8));
                bbuf.dirty = true;
                log.write(bbuf);
            } finally {
                bcache.brelse(bbuf);
            }

            // Zero the freed block
            Buffer b = bcache.bread(blockNum);
            try {
                Arrays.fill(b.data, (byte) 0);
                b.dirty = true;
                log.write(b);
            } finally {
                bcache.brelse(b);
            }
        } catch (Exception e) {
            throw new RuntimeException("bfree: failed to free block", e);
        }
    }
}
