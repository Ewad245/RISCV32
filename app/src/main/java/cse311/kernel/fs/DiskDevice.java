package cse311.kernel.fs;

import java.io.RandomAccessFile;
import java.io.IOException;

@SuppressWarnings({
    "PMD.UnusedPrivateField",
    "PMD.AvoidPrintStackTrace",
    "PMD.EmptyCatchBlock"
})
public class DiskDevice {
    public static final int BSIZE = 1024; // Block size

    private RandomAccessFile diskImage;
    private final String imagePath;

    public DiskDevice(String imagePath) {
        this.imagePath = imagePath;
        try {
            // "rw" mode creates the file if it doesn't exist
            this.diskImage = new RandomAccessFile(imagePath, "rw");
        } catch (IOException e) {
            throw new RuntimeException("Could not open disk image: " + imagePath, e);
        }
    }

    public void read(int blockNo, byte[] buffer) {
        if (buffer.length != BSIZE)
            throw new IllegalArgumentException("Buffer must be BSIZE");
        try {
            diskImage.seek((long) blockNo * BSIZE);
            int read = diskImage.read(buffer);
            if (read == -1) {
                // End of file, return zeroes
                for (int i = 0; i < BSIZE; i++)
                    buffer[i] = 0;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void write(int blockNo, byte[] buffer) {
        if (buffer.length != BSIZE)
            throw new IllegalArgumentException("Buffer must be BSIZE");
        try {
            diskImage.seek((long) blockNo * BSIZE);
            diskImage.write(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            diskImage.close();
        } catch (IOException e) {
        }
    }
}
