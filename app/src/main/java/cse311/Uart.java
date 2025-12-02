package cse311;

import java.util.concurrent.locks.ReentrantLock;

public class Uart {
    private static final int TX_READY = 0x20;
    private static final int RX_READY = 0x01;

    private int status;
    private int control;

    // --- FIFO Ring Buffer Variables ---
    private byte[] rxBuffer;
    private int rxHead; // Read from here
    private int rxTail; // Write to here
    private int rxCount; // Bytes available

    private final ReentrantLock lock = new ReentrantLock();

    public Uart() {
        status = TX_READY;
        control = 0;
        rxBuffer = new byte[2048];
        rxHead = 0;
        rxTail = 0;
        rxCount = 0;
    }

    public int read(int address) {
        lock.lock();
        try {
            switch (address - MemoryManager.UART_BASE) {
                case 0x0: // TX Data
                    return 0;
                case 0x4: // RX Data
                    // FIFO Read
                    if (rxCount > 0) {
                        byte data = rxBuffer[rxHead];
                        rxHead = (rxHead + 1) % rxBuffer.length;
                        rxCount--;
                        if (rxCount == 0)
                            status &= ~RX_READY;
                        return data & 0xFF;
                    }
                    return 0;
                case 0x8: // Status
                    return status | TX_READY;
                case 0xC: // Control
                    return control;
                default:
                    return 0;
            }
        } finally {
            lock.unlock();
        }
    }

    public void write(int address, int value) {
        lock.lock();
        try {
            switch (address - MemoryManager.UART_BASE) {
                case 0x0:
                    System.out.write(value & 0xFF);
                    System.out.flush();
                    break;
                case 0xC:
                    control = value;
                    break;
            }
        } finally {
            lock.unlock();
        }
    }

    public void receiveData(byte data) {
        lock.lock();
        try {
            // FIFO Write
            if (rxCount < rxBuffer.length) {
                rxBuffer[rxTail] = data;
                rxTail = (rxTail + 1) % rxBuffer.length;
                rxCount++;
                status |= RX_READY;
            }
        } finally {
            lock.unlock();
        }
    }

    public void receiveDatas(byte[] data) {
        for (byte b : data)
            receiveData(b);
    }
}