package cse311;

import cse311.kernel.fs.Buffer;
import cse311.kernel.fs.BufferCache;
import cse311.kernel.fs.DirectoryEntry;
import cse311.kernel.fs.DiskDevice;
import cse311.kernel.fs.Pipe;
import cse311.kernel.fs.SuperBlock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSystemTest {

    private File tempDiskFile;
    private DiskDevice diskDevice;

    @BeforeEach
    void setUp() throws IOException {
        tempDiskFile = File.createTempFile("fs_test_disk", ".img");
        tempDiskFile.deleteOnExit();

        try (RandomAccessFile raf = new RandomAccessFile(tempDiskFile, "rw")) {
            raf.setLength(100 * DiskDevice.BSIZE);

            ByteBuffer bb = ByteBuffer.allocate(DiskDevice.BSIZE).order(ByteOrder.LITTLE_ENDIAN);
            bb.putInt(100); // size
            bb.putInt(80);  // nblocks
            bb.putInt(10);  // ninodes
            bb.putInt(5);   // nlog
            bb.putInt(2);   // logstart
            bb.putInt(7);   // inodestart
            bb.putInt(8);   // bmapstart

            raf.seek((long) 1 * DiskDevice.BSIZE);
            raf.write(bb.array());
        }

        diskDevice = new DiskDevice(tempDiskFile.getAbsolutePath());
    }

    @AfterEach
    void tearDown() {
        if (diskDevice != null) {
            diskDevice.close();
        }
        if (tempDiskFile != null && tempDiskFile.exists()) {
            tempDiskFile.delete();
        }
    }

    @Test
    void testDiskDeviceReadWrite() {
        byte[] writeData = new byte[DiskDevice.BSIZE];
        for (int i = 0; i < writeData.length; i++) {
            writeData[i] = (byte) (i % 256);
        }

        diskDevice.write(10, writeData);

        byte[] readData = new byte[DiskDevice.BSIZE];
        diskDevice.read(10, readData);

        assertArrayEquals(writeData, readData, "Data read from disk should match data written");
    }

    @Test
    void testSuperBlockRead() {
        SuperBlock sb = SuperBlock.read(diskDevice);

        assertAll("superBlockFields",
            () -> assertNotNull(sb, "SuperBlock should not be null"),
            () -> assertEquals(100, sb.size, "Size should be 100"),
            () -> assertEquals(80, sb.nblocks, "Data blocks should be 80"),
            () -> assertEquals(10, sb.ninodes, "Inodes count should be 10"),
            () -> assertEquals(5, sb.nlog, "Log size should be 5"),
            () -> assertEquals(2, sb.logstart, "Log start block should be 2"),
            () -> assertEquals(7, sb.inodestart, "Inode start block should be 7"),
            () -> assertEquals(8, sb.bmapstart, "Bitmap start block should be 8")
        );
    }

    @Test
    void testBufferCacheReadWriteAndEviction() throws Exception {
        BufferCache bcache = new BufferCache(diskDevice);

        Buffer b1 = bcache.bread(15);
        int initialBlockNo = b1.blockNo;

        b1.data[0] = 0x42;
        b1.data[1] = 0x43;
        bcache.bwrite(b1);
        bcache.brelse(b1);

        Buffer b2 = bcache.bread(15);
        byte val0 = b2.data[0];
        byte val1 = b2.data[1];
        bcache.brelse(b2);

        assertAll("bufferCacheState",
            () -> assertEquals(15, initialBlockNo, "Block number should be 15"),
            () -> assertEquals(0x42, val0, "Byte 0 should persist via buffer cache"),
            () -> assertEquals(0x43, val1, "Byte 1 should persist via buffer cache")
        );
    }

    @Test
    void testPipeReadWriteAndBufferState() {
        Pipe pipe = new Pipe();
        boolean initEmpty = pipe.isEmpty();
        boolean initFull = pipe.isFull();
        int initAvail = pipe.availableBytes();
        int initFree = pipe.freeSpace();

        byte[] data = "HELLO".getBytes(StandardCharsets.UTF_8);
        for (byte b : data) {
            pipe.writeByte(b);
        }

        boolean postWriteEmpty = pipe.isEmpty();
        int postWriteAvail = pipe.availableBytes();
        int postWriteFree = pipe.freeSpace();

        char c1 = (char) pipe.readByte();
        char c2 = (char) pipe.readByte();
        char c3 = (char) pipe.readByte();
        char c4 = (char) pipe.readByte();
        char c5 = (char) pipe.readByte();
        int emptyRead = pipe.readByte();

        assertAll("pipeState",
            () -> assertTrue(initEmpty, "Initial pipe must be empty"),
            () -> assertFalse(initFull, "Initial pipe must not be full"),
            () -> assertEquals(0, initAvail, "Initial available bytes must be 0"),
            () -> assertEquals(Pipe.PIPESIZE, initFree, "Initial free space must be PIPESIZE"),
            () -> assertFalse(postWriteEmpty, "Pipe should not be empty after writing"),
            () -> assertEquals(5, postWriteAvail, "Available bytes should be 5"),
            () -> assertEquals(Pipe.PIPESIZE - 5, postWriteFree, "Free space should be PIPESIZE - 5"),
            () -> assertEquals('H', c1, "First char should be H"),
            () -> assertEquals('E', c2, "Second char should be E"),
            () -> assertEquals('L', c3, "Third char should be L"),
            () -> assertEquals('L', c4, "Fourth char should be L"),
            () -> assertEquals('O', c5, "Fifth char should be O"),
            () -> assertEquals(-1, emptyRead, "Read from empty pipe should return -1")
        );
    }

    @Test
    void testDirectoryEntrySerialization() {
        DirectoryEntry de = new DirectoryEntry(42, "myfile.txt");
        byte[] bytes = de.toBytes();
        DirectoryEntry deserialized = DirectoryEntry.fromBytes(bytes);

        assertAll("dirEntrySerialization",
            () -> assertEquals(42, de.inum, "Inum should be 42"),
            () -> assertEquals("myfile.txt", de.name, "Name should match"),
            () -> assertEquals(DirectoryEntry.DIRSIZ + 2, bytes.length, "Serialized size should match"),
            () -> assertEquals(42, deserialized.inum, "Deserialized inum should match"),
            () -> assertEquals("myfile.txt", deserialized.name, "Deserialized name should match")
        );
    }
}
