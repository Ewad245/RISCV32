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

    /**
     * Post-construction initialization for system directories (e.g. /tmp).
     */
    public void initializeSystemDirectories() {
        createDirectoryIfMissing("/tmp");
    }

    public final void createDirectoryIfMissing(String path) {
        StringBuilder nameBuf = new StringBuilder();
        Inode dp = nameiparent(null, path, nameBuf);
        if (dp != null && nameBuf.length() > 0) {
            String name = nameBuf.toString();
            Inode existing = dirlookup(dp, name);
            if (existing == null) {
                log.beginOp();
                try {
                    Inode ip = ialloc(Inode.T_DIR);
                    if (ip != null) {
                        ip.nlink = 2;
                        updateInode(ip);
                        dirlink(ip, ".", ip.inum);
                        dirlink(ip, "..", dp.inum);
                        dp.nlink++;
                        updateInode(dp);
                        dirlink(dp, name, ip.inum);
                        iput(ip);
                    }
                } finally {
                    log.endOp();
                }
            } else {
                iput(existing);
            }
            iput(dp);
        }
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
                    for (int i = 0; i < Inode.NDIRECT + 2; i++) {
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
                for (int i = 0; i < Inode.NDIRECT + 2; i++) {
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

        int nindirect = DiskDevice.BSIZE / 4; // 256

        // 1. Singly Indirect Block (Index NDIRECT = 11)
        if (logicalBlock < nindirect) {
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
        logicalBlock -= nindirect;

        // 2. Doubly Indirect Block (Index NDIRECT + 1 = 12)
        if (logicalBlock < nindirect * nindirect) {
            int doublyBlock = ip.addrs[Inode.NDIRECT + 1];
            if (allocate && doublyBlock == 0) {
                doublyBlock = balloc();
                ip.addrs[Inode.NDIRECT + 1] = doublyBlock;
            }
            if (doublyBlock == 0)
                return 0; // Hole

            int index1 = logicalBlock / nindirect;
            int index2 = logicalBlock % nindirect;

            try {
                Buffer b1 = bcache.bread(doublyBlock);
                try {
                    ByteBuffer bb1 = ByteBuffer.wrap(b1.data).order(ByteOrder.LITTLE_ENDIAN);
                    int singleBlock = bb1.getInt(index1 * 4);
                    if (allocate && singleBlock == 0) {
                        singleBlock = balloc();
                        bb1.putInt(index1 * 4, singleBlock);
                        b1.dirty = true;
                        log.write(b1);
                    }
                    if (singleBlock == 0)
                        return 0;

                    Buffer b2 = bcache.bread(singleBlock);
                    try {
                        ByteBuffer bb2 = ByteBuffer.wrap(b2.data).order(ByteOrder.LITTLE_ENDIAN);
                        int physBlock = bb2.getInt(index2 * 4);
                        if (allocate && physBlock == 0) {
                            physBlock = balloc();
                            bb2.putInt(index2 * 4, physBlock);
                            b2.dirty = true;
                            log.write(b2);
                        }
                        return physBlock;
                    } finally {
                        bcache.brelse(b2);
                    }
                } finally {
                    bcache.brelse(b1);
                }
            } catch (Exception e) {
                throw new RuntimeException("mapBlock: failed to read doubly indirect block", e);
            }
        }

        throw new RuntimeException("File too large (exceeds doubly indirect max size)");
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

        // Max size with 11 direct, 1 indirect (256), 1 doubly-indirect (65536) = 65803 blocks = 67.3 MB
        long maxBlocks = (long) Inode.NDIRECT + (DiskDevice.BSIZE / 4) + (long) (DiskDevice.BSIZE / 4) * (DiskDevice.BSIZE / 4);
        long maxBytes = maxBlocks * DiskDevice.BSIZE;
        if (off + n > maxBytes) {
            n = (int) (maxBytes - off);
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
     * Splits and normalizes path components, stripping empty components and handling '.' and '..'.
     */
    private java.util.List<String> parsePathComponents(String path) {
        if (path == null)
            return java.util.Collections.emptyList();
        String[] rawParts = path.split("/+");
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (String part : rawParts) {
            if (part.isEmpty() || part.equals("."))
                continue;
            parts.add(part);
        }
        return parts;
    }

    /**
     * Resolve a path (e.g., "/home/test") to an Inode.
     * Starts from root if path is absolute, otherwise from task's cwd.
     */
    public final Inode namei(Task task, String path) {
        if (path == null || path.isEmpty())
            return null;

        java.util.List<String> parts = parsePathComponents(path);
        Inode ip;
        if (path.startsWith("/")) {
            ip = iget(1); // Root
        } else {
            ip = (task != null && task.cwd != null) ? idup(task.cwd) : iget(1);
        }

        if (parts.isEmpty()) {
            return ip; // Root or cwd
        }

        for (String part : parts) {
            Inode next = dirlookup(ip, part);
            iput(ip); // Release the parent before moving down

            if (next == null)
                return null;
            ip = next;
        }
        return ip;
    }

    public final Inode namei(String path) {
        return namei(null, path);
    }

    /**
     * Resolves the parent directory of a path, and returns the final path component
     * in the provided StringBuilder.
     */
    public final Inode nameiparent(Task task, String path, StringBuilder name) {
        if (path == null || path.isEmpty())
            return null;

        java.util.List<String> parts = parsePathComponents(path);
        if (parts.isEmpty())
            return null;

        Inode ip;
        if (path.startsWith("/")) {
            ip = iget(1); // Root
        } else {
            ip = (task != null && task.cwd != null) ? idup(task.cwd) : iget(1);
        }

        for (int i = 0; i < parts.size() - 1; i++) {
            String part = parts.get(i);
            Inode next = dirlookup(ip, part);
            iput(ip); // Release the parent before moving down
            if (next == null)
                return null;
            ip = next;
        }

        name.append(parts.get(parts.size() - 1));
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

            bfree(ip.addrs[Inode.NDIRECT]);
            ip.addrs[Inode.NDIRECT] = 0;
        }

        // 3. Free doubly-indirect blocks
        if (ip.addrs[Inode.NDIRECT + 1] != 0) {
            try {
                Buffer b1 = bcache.bread(ip.addrs[Inode.NDIRECT + 1]);
                try {
                    ByteBuffer buf1 = ByteBuffer.wrap(b1.data).order(ByteOrder.LITTLE_ENDIAN);
                    for (int i = 0; i < DiskDevice.BSIZE / 4; i++) {
                        int singleBlock = buf1.getInt();
                        if (singleBlock != 0) {
                            Buffer b2 = bcache.bread(singleBlock);
                            try {
                                ByteBuffer buf2 = ByteBuffer.wrap(b2.data).order(ByteOrder.LITTLE_ENDIAN);
                                for (int j = 0; j < DiskDevice.BSIZE / 4; j++) {
                                    int phys = buf2.getInt();
                                    if (phys != 0) {
                                        bfree(phys);
                                    }
                                }
                            } finally {
                                bcache.brelse(b2);
                            }
                            bfree(singleBlock);
                        }
                    }
                } finally {
                    bcache.brelse(b1);
                }
            } catch (Exception e) {
                throw new RuntimeException("truncate: failed to read doubly-indirect block", e);
            }

            bfree(ip.addrs[Inode.NDIRECT + 1]);
            ip.addrs[Inode.NDIRECT + 1] = 0;
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
