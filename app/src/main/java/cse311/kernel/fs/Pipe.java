package cse311.kernel.fs;

public class Pipe {
    public static final int PIPESIZE = 4096;
    private final byte[] buffer = new byte[PIPESIZE];
    private int readIndex = 0;
    private int writeIndex = 0;
    private boolean readOpen = true;
    private boolean writeOpen = true;

    public synchronized boolean isEmpty() {
        return readIndex == writeIndex;
    }

    public synchronized boolean isFull() {
        return writeIndex - readIndex >= PIPESIZE;
    }

    public synchronized int readByte() {
        if (isEmpty()) return -1;
        int data = buffer[readIndex % PIPESIZE] & 0xFF;
        readIndex++;
        return data;
    }

    public synchronized boolean writeByte(byte data) {
        if (isFull()) return false;
        buffer[writeIndex % PIPESIZE] = data;
        writeIndex++;
        return true;
    }

    public synchronized int availableBytes() {
        return writeIndex - readIndex;
    }

    public synchronized int freeSpace() {
        return PIPESIZE - (writeIndex - readIndex);
    }

    public boolean isReadOpen() { return readOpen; }
    public boolean isWriteOpen() { return writeOpen; }
    
    public synchronized void closeRead() { readOpen = false; }
    public synchronized void closeWrite() { writeOpen = false; }
}
