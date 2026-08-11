package cse311;

import cse311.Constants.OSConstants;
import cse311.kernel.fs.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Mkfs: Generates the xv6-compatible fs.img.
 * Layout:
 * [ boot | sb | log | inodes | bit | data ... ]
 */
@SuppressWarnings({
    "PMD.SystemPrintln",
    "PMD.AssignmentInOperand",
    "PMD.AvoidReassigningParameters"
})
public class Mkfs {
    // Disk Configuration
    static final int NBLOCKS = 20000; // Total disk size
    static final int NINODES = 500; // Max number of files
    static final int NLOG = 100;
    static final int BSIZE = 1024; // Block size

    // Derived Offsets
    static int nbitmap = NBLOCKS / (BSIZE * 8) + 1;
    static int ninodeblocks = NINODES / Inode.IPB + 1;
    static int logstart = 2;
    static int inodestart = 2 + NLOG;
    static int bmapstart = 2 + NLOG + ninodeblocks;
    static int datastart = bmapstart + nbitmap;

    // Global cursor for the next free data block
    static int freeblock = datastart;

    // Static inode allocator counter
    static int nextInode = 1;

    public static void main(String[] args) throws Exception {
        // 1. Setup paths
        String fsPath = "app" + OSConstants.file_seperator + "src" + OSConstants.file_seperator + "main"
                + OSConstants.file_seperator + "resources" + OSConstants.file_seperator + "fs.img";
        File userDir = new File("app" + OSConstants.file_seperator + "src" + OSConstants.file_seperator + "main"
                + OSConstants.file_seperator +
                "resources" + OSConstants.file_seperator + "user_programs");

        System.out.println("Creating " + fsPath + " with blocks=" + NBLOCKS);

        try (RandomAccessFile disk = new RandomAccessFile(fsPath, "rw")) {
            // Zero out the disk first
            disk.setLength(0);
            disk.setLength((long) NBLOCKS * BSIZE);

            // 2. Write Superblock (Block 1)
            writeSuperBlock(disk);

            // 3. Allocate Root Inode (Inode 1)
            // xv6 root inode is usually 1.
            int rootInum = ialloc(disk, Inode.T_DIR);

            // Add . and .. to Root
            appendDir(disk, rootInum, new DirectoryEntry(rootInum, "."));
            appendDir(disk, rootInum, new DirectoryEntry(rootInum, ".."));

            // 4. Scan for binaries/directories and recursively add them
            if (userDir.exists() && userDir.isDirectory()) {
                importDirectoryContents(disk, rootInum, userDir);
            } else {
                System.out.println("Warning: No user_programs directory found at " + userDir.getAbsolutePath());
            }

            // 5. Write the final free block bitmap
            // This is required for our balloc() implementation in FileSystem.java
            System.out.println("Writing free block bitmap... Used blocks: " + freeblock);
            byte[] bitmap = new byte[BSIZE * nbitmap];
            for (int i = 0; i < freeblock; i++) {
                bitmap[i / 8] |= (1 << (i % 8)); // Mark as used
            }
            disk.seek((long) bmapstart * BSIZE);
            disk.write(bitmap);

            System.out.println("File System created successfully.");
        }
    }

    // --- Core Operations ---

    private static void importDirectoryContents(RandomAccessFile disk, int parentInum, File dir) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.getName().startsWith(".")) {
                continue;
            }

            if (file.isDirectory()) {
                System.out.println("Creating directory: " + file.getName());

                int subDirInum = ialloc(disk, Inode.T_DIR);
                appendDir(disk, subDirInum, new DirectoryEntry(subDirInum, "."));
                appendDir(disk, subDirInum, new DirectoryEntry(parentInum, ".."));

                DirectoryEntry de = new DirectoryEntry(subDirInum, file.getName());
                appendDir(disk, parentInum, de);

                importDirectoryContents(disk, subDirInum, file);
            } else if (file.isFile()) {
                System.out.println("Copying file: " + file.getName());

                int fileInum = ialloc(disk, Inode.T_FILE);
                writeFileContent(disk, fileInum, file);

                DirectoryEntry de = new DirectoryEntry(fileInum, file.getName());
                appendDir(disk, parentInum, de);
            }
        }
    }

    private static void writeSuperBlock(RandomAccessFile disk) throws Exception {
        ByteBuffer bb = ByteBuffer.allocate(BSIZE).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(NBLOCKS); // size
        bb.putInt(NBLOCKS - datastart); // nblocks (data blocks)
        bb.putInt(NINODES); // ninodes
        bb.putInt(NLOG); // nlog
        bb.putInt(logstart); // logstart
        bb.putInt(inodestart); // inodestart
        bb.putInt(bmapstart); // bmapstart

        disk.seek(1 * BSIZE);
        disk.write(bb.array());
    }

    /**
     * Allocates a new inode on disk and returns its number.
     */
    private static int ialloc(RandomAccessFile disk, short type) throws Exception {
        int inum = nextInode++;

        Inode ip = new Inode();
        ip.inum = inum;
        ip.type = type;
        ip.nlink = 1;
        ip.size = 0;

        writeInode(disk, ip);
        return inum;
    }

    /**
     * Appends a directory entry to a directory inode.
     */
    private static void appendDir(RandomAccessFile disk, int inum, DirectoryEntry de) throws Exception {
        byte[] data = de.toBytes();
        appendData(disk, inum, data);
    }

    /**
     * Reads a host file and writes it into the target inode.
     */
    private static void writeFileContent(RandomAccessFile disk, int inum, File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[BSIZE];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                // If read partial block, trim it
                if (bytesRead < BSIZE) {
                    byte[] exact = Arrays.copyOf(buffer, bytesRead);
                    appendData(disk, inum, exact);
                } else {
                    appendData(disk, inum, buffer);
                }
            }
        }
    }

    /**
     * Appends data to an inode, allocating blocks/indirect blocks as needed.
     */
    private static void appendData(RandomAccessFile disk, int inum, byte[] data) throws Exception {
        Inode ip = readInode(disk, inum);
        int offset = ip.size;
        int remaining = data.length;
        int bufIdx = 0;

        while (remaining > 0) {
            int blockIdx = offset / BSIZE;

            // Map logical block index to physical block number
            int physBlock = mapBlock(disk, ip, blockIdx);

            // Calculate how much we can write to this block
            int blockOff = offset % BSIZE;
            int toWrite = Math.min(remaining, BSIZE - blockOff);

            // Read existing block (in case we are appending to the middle)
            byte[] blockBuf = new byte[BSIZE];
            if (blockOff > 0) {
                disk.seek((long) physBlock * BSIZE);
                disk.read(blockBuf);
            }

            // Copy data
            System.arraycopy(data, bufIdx, blockBuf, blockOff, toWrite);

            // Write back
            disk.seek((long) physBlock * BSIZE);
            disk.write(blockBuf);

            remaining -= toWrite;
            bufIdx += toWrite;
            offset += toWrite;
        }

        ip.size = offset;
        writeInode(disk, ip);
    }

    /**
     * Returns the physical block number for a logical index.
     * Allocates if it doesn't exist.
     */
    private static int mapBlock(RandomAccessFile disk, Inode ip, int logicalBlock) throws Exception {
        // 1. Direct Blocks (0-10)
        if (logicalBlock < Inode.NDIRECT) {
            if (ip.addrs[logicalBlock] == 0) {
                ip.addrs[logicalBlock] = allocBlock();
                writeInode(disk, ip); // Save allocation
            }
            return ip.addrs[logicalBlock];
        }

        // 2. Singly Indirect Block (11)
        logicalBlock -= Inode.NDIRECT;
        int nindirect = BSIZE / 4; // 256
        if (logicalBlock < nindirect) {
            // Allocate the indirect block itself if missing
            if (ip.addrs[Inode.NDIRECT] == 0) {
                ip.addrs[Inode.NDIRECT] = allocBlock();
                writeInode(disk, ip);
            }
            int indirectBlockPhys = ip.addrs[Inode.NDIRECT];

            // Read Indirect Block
            byte[] buf = new byte[BSIZE];
            disk.seek((long) indirectBlockPhys * BSIZE);
            disk.read(buf);
            ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);

            // Get existing mapping
            int phys = bb.getInt(logicalBlock * 4);
            if (phys == 0) {
                phys = allocBlock();
                bb.putInt(logicalBlock * 4, phys);
                // Write back indirect block
                disk.seek((long) indirectBlockPhys * BSIZE);
                disk.write(buf);
            }
            return phys;
        }

        // 3. Doubly Indirect Block (12)
        logicalBlock -= nindirect;
        if (logicalBlock < nindirect * nindirect) {
            if (ip.addrs[Inode.NDIRECT + 1] == 0) {
                ip.addrs[Inode.NDIRECT + 1] = allocBlock();
                writeInode(disk, ip);
            }
            int doublyBlockPhys = ip.addrs[Inode.NDIRECT + 1];

            int index1 = logicalBlock / nindirect;
            int index2 = logicalBlock % nindirect;

            // Read Level-1 Indirect Table
            byte[] buf1 = new byte[BSIZE];
            disk.seek((long) doublyBlockPhys * BSIZE);
            disk.read(buf1);
            ByteBuffer bb1 = ByteBuffer.wrap(buf1).order(ByteOrder.LITTLE_ENDIAN);

            int singleBlockPhys = bb1.getInt(index1 * 4);
            if (singleBlockPhys == 0) {
                singleBlockPhys = allocBlock();
                bb1.putInt(index1 * 4, singleBlockPhys);
                disk.seek((long) doublyBlockPhys * BSIZE);
                disk.write(buf1);
            }

            // Read Level-2 Indirect Table
            byte[] buf2 = new byte[BSIZE];
            disk.seek((long) singleBlockPhys * BSIZE);
            disk.read(buf2);
            ByteBuffer bb2 = ByteBuffer.wrap(buf2).order(ByteOrder.LITTLE_ENDIAN);

            int phys = bb2.getInt(index2 * 4);
            if (phys == 0) {
                phys = allocBlock();
                bb2.putInt(index2 * 4, phys);
                disk.seek((long) singleBlockPhys * BSIZE);
                disk.write(buf2);
            }
            return phys;
        }

        throw new RuntimeException("File too large for Mkfs: " + logicalBlock);
    }

    private static int allocBlock() {
        if (freeblock >= NBLOCKS)
            throw new RuntimeException("Disk full!");
        return freeblock++;
    }

    // --- Inode IO Helpers ---

    private static Inode readInode(RandomAccessFile disk, int inum) throws Exception {
        Inode ip = new Inode();
        ip.inum = inum;

        long addr = getInodeAddr(inum);
        disk.seek(addr);

        byte[] buf = new byte[64]; // sizeof(Inode)
        disk.read(buf);
        ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);

        ip.type = bb.getShort();
        ip.major = bb.getShort();
        ip.minor = bb.getShort();
        ip.nlink = bb.getShort();
        ip.size = bb.getInt();
        for (int i = 0; i < Inode.NDIRECT + 2; i++)
            ip.addrs[i] = bb.getInt();

        return ip;
    }

    private static void writeInode(RandomAccessFile disk, Inode ip) throws Exception {
        long addr = getInodeAddr(ip.inum);
        disk.seek(addr);

        ByteBuffer bb = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        bb.putShort(ip.type);
        bb.putShort(ip.major);
        bb.putShort(ip.minor);
        bb.putShort(ip.nlink);
        bb.putInt(ip.size);
        for (int i = 0; i < Inode.NDIRECT + 2; i++)
            bb.putInt(ip.addrs[i]);

        disk.write(bb.array());
    }

    private static long getInodeAddr(int inum) {
        int blockNum = inodestart + (inum / Inode.IPB);
        int offset = (inum % Inode.IPB) * 64;
        return (long) blockNum * BSIZE + offset;
    }
}
