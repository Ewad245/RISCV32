package cse311.kernel.fs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import cse311.kernel.process.Task;

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

    // Allocate a new inode with the given type
    public Inode ialloc(short type) {
        for (int inum = 1; inum < sb.ninodes; inum++) {
            Inode ip = getInode(inum);
            if (ip.type == 0) {
                // Free inode found
                ip.type = type;
                ip.size = 0;
                updateInode(ip);
                return ip;
            }
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
                // Found it! Return the inode.
                return getInode(de.inum);
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
            ip = getInode(1); // Root
        } else {
            ip = (task != null && task.cwd != null) ? task.cwd : getInode(1);
        }

        String[] parts = path.split("/");
        for (String part : parts) {
            if (part.isEmpty() || part.equals("."))
                continue;
            Inode next = dirlookup(ip, part);
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
            ip = getInode(1); // Root
        } else {
            ip = (task != null && task.cwd != null) ? task.cwd : getInode(1);
        }

        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.equals("."))
                continue;
            Inode next = dirlookup(ip, part);
            if (next == null)
                return null;
            ip = next;
        }

        if (parts.length > 0) {
            name.append(parts[parts.length - 1]);
        }
        return ip;
    }
}
