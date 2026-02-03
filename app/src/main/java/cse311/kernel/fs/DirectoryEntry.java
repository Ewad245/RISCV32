package cse311.kernel.fs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class DirectoryEntry {
    public static final int DIRSIZ = 14; // Max filename length
    public static final int SIZE = 16; // 2 bytes inum + 14 bytes name

    public int inum; // Inode number (unsigned short on disk)
    public String name; // Filename

    public DirectoryEntry(int inum, String name) {
        this.inum = inum;
        this.name = name;
        if (this.name.length() > DIRSIZ) {
            this.name = this.name.substring(0, DIRSIZ);
        }
    }

    // Serialize to bytes for disk writing
    public byte[] toBytes() {
        byte[] buf = new byte[SIZE];
        ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
        bb.putShort((short) inum);

        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < DIRSIZ; i++) {
            if (i < nameBytes.length)
                bb.put(nameBytes[i]);
            else
                bb.put((byte) 0); // Padding
        }
        return buf;
    }

    // Parse from bytes read from disk
    public static DirectoryEntry fromBytes(byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int inum = Short.toUnsignedInt(bb.getShort());

        byte[] nameBytes = new byte[DIRSIZ];
        bb.get(nameBytes);

        // Trim nulls
        int len = 0;
        while (len < DIRSIZ && nameBytes[len] != 0)
            len++;
        String name = new String(nameBytes, 0, len, StandardCharsets.UTF_8);

        return new DirectoryEntry(inum, name);
    }
}
