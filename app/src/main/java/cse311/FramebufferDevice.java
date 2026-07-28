package cse311;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MMIO Framebuffer & Input Device for DOOM (doomgeneric) and Graphical Apps.
 * Maps 320x200 32-bit ARGB/RGBA pixels directly to high-performance off-heap memory.
 */
public class FramebufferDevice {

    public static final int WIDTH = 320;
    public static final int HEIGHT = 200;
    public static final int FB_SIZE_BYTES = WIDTH * HEIGHT * Integer.BYTES; // 256,000 bytes

    // MMIO Base Addresses
    public static final int FB_BASE = 0x11000000;
    public static final int FB_END = FB_BASE + FB_SIZE_BYTES; // 0x1103E800

    public static final int CTRL_BASE = 0x1103F000;
    public static final int REG_WIDTH  = CTRL_BASE + 0x0;  // Read 320
    public static final int REG_HEIGHT = CTRL_BASE + 0x4;  // Read 200
    public static final int REG_FLUSH  = CTRL_BASE + 0x8;  // Write 1 to notify frame complete
    public static final int REG_KEY_DATA = CTRL_BASE + 0x10; // Read key code
    public static final int REG_KEY_STAT = CTRL_BASE + 0x14; // Read key state (1=pressed, 0=released / empty)
    public static final int REG_TIME_MS  = CTRL_BASE + 0x20; // Read hardware timer (real-time ms)

    private final ByteBuffer directByteBuffer;
    private final IntBuffer directIntBuffer;
    private final AtomicBoolean frameReady = new AtomicBoolean(false);

    // Keyboard Event FIFO Queue for doomgeneric input polling
    public static class KeyEventData {
        public final int keyCode;
        public final boolean pressed;

        public KeyEventData(int keyCode, boolean pressed) {
            this.keyCode = keyCode;
            this.pressed = pressed;
        }
    }

    private final Queue<KeyEventData> keyQueue = new ConcurrentLinkedQueue<>();
    private final long startTimeMs = System.currentTimeMillis();

    public FramebufferDevice() {
        // Direct allocation guarantees off-heap zero-copy sharing with JavaFX PixelBuffer
        this.directByteBuffer = ByteBuffer.allocateDirect(FB_SIZE_BYTES);
        this.directByteBuffer.order(ByteOrder.nativeOrder());
        this.directIntBuffer = this.directByteBuffer.asIntBuffer();
    }

    public IntBuffer getDirectIntBuffer() {
        return directIntBuffer;
    }

    public AtomicBoolean getFrameReadyFlag() {
        return frameReady;
    }

    public void pushKeyEvent(int keyCode, boolean pressed) {
        keyQueue.offer(new KeyEventData(keyCode, pressed));
    }

    // MMIO Access Handlers
    public boolean isFramebufferAccess(int address) {
        return address >= FB_BASE && address < FB_END;
    }

    public boolean isControlAccess(int address) {
        return address >= CTRL_BASE && address < CTRL_BASE + 0x100;
    }

    private volatile boolean hasCurrentKey;
    private volatile int currentKeyCode;
    private volatile int currentKeyState;

    public byte readByte(int address) {
        if (isFramebufferAccess(address)) {
            int offset = address - FB_BASE;
            return directByteBuffer.get(offset);
        }
        return 0;
    }

    public int readWord(int address) {
        if (isFramebufferAccess(address)) {
            int offset = address - FB_BASE;
            int pixelIndex = offset / 4;
            if (pixelIndex >= 0 && pixelIndex < directIntBuffer.capacity()) {
                return directIntBuffer.get(pixelIndex);
            }
            return 0;
        }

        if (isControlAccess(address)) {
            if (address == REG_WIDTH) {
                return WIDTH;
            }
            if (address == REG_HEIGHT) {
                return HEIGHT;
            }
            if (address == REG_KEY_DATA) {
                hasCurrentKey = false;
                return currentKeyCode;
            }
            if (address == REG_TIME_MS) {
                // Return real host system time in milliseconds for accurate DOOM frame timing
                return (int) ((System.currentTimeMillis() - startTimeMs) & 0x7FFFFFFF);
            }
            if (address == REG_KEY_STAT) {
                if (hasCurrentKey) {
                    return currentKeyState;
                }
                KeyEventData event = keyQueue.poll();
                if (event != null) {
                    currentKeyCode = event.keyCode;
                    currentKeyState = event.pressed ? 1 : 2; // 1 = pressed, 2 = released
                    hasCurrentKey = true;
                    return currentKeyState;
                }
                return 0;
            }
        }
        return 0;
    }

    public void writeByte(int address, byte value) {
        if (isFramebufferAccess(address)) {
            int offset = address - FB_BASE;
            directByteBuffer.put(offset, value);
        }
    }

    public void writeWordDirect(int pixelIndex, int value) {
        if (pixelIndex >= 0 && pixelIndex < directIntBuffer.capacity()) {
            directIntBuffer.put(pixelIndex, value | 0xFF000000);
        }
    }

    public void writeWord(int address, int value) {
        if (isFramebufferAccess(address)) {
            int offset = address - FB_BASE;
            int pixelIndex = offset / 4;
            writeWordDirect(pixelIndex, value);
            return;
        }

        if (isControlAccess(address) && address == REG_FLUSH) {
            frameReady.set(true);
        }
    }
}
