package cse311;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * MMIO Audio Hardware Device for DOOM and RISC-V 32 Graphical Apps.
 * Provides real-time PCM audio playback via javax.sound.sampled.
 */
public class AudioDevice {

    public static final int AUDIO_BASE = 0x11040000;
    public static final int REG_CTRL        = AUDIO_BASE + 0x0; // Control (Bit 0: Enabled)
    public static final int REG_FIFO_FREE   = AUDIO_BASE + 0x4; // Queue free space count
    public static final int REG_FIFO_DATA   = AUDIO_BASE + 0x8; // Write 16-bit PCM sample
    public static final int REG_SAMPLE_RATE = AUDIO_BASE + 0xC; // Default 11025 Hz

    private static final int QUEUE_CAPACITY = 2048;
    private final BlockingQueue<Short> sampleQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    private SourceDataLine audioLine;
    private Thread playbackThread;
    private volatile boolean enabled = true;
    private volatile int sampleRate = 11025;

    public AudioDevice() {
        initAudioSystem(sampleRate);
    }

    private synchronized void initAudioSystem(int rate) {
        try {
            if (audioLine != null && audioLine.isOpen()) {
                audioLine.stop();
                audioLine.close();
            }
            AudioFormat format = new AudioFormat((float) rate, 16, 1, true, false); // 16-bit Mono Little-Endian
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            if (AudioSystem.isLineSupported(info)) {
                audioLine = (SourceDataLine) AudioSystem.getLine(info);
                audioLine.open(format, 1024); // Ultra-low latency hardware buffer (~46ms)
                audioLine.start();

                playbackThread = new Thread(this::playbackLoop, "AudioPlaybackThread");
                playbackThread.setDaemon(true);
                playbackThread.start();
            }
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            cse311.Logger.FileLogger.log(cse311.Logger.FileLogger.LogLevel.ERROR,
                    "AudioDevice initialization failed: " + e.getMessage());
        }
    }

    private void playbackLoop() {
        byte[] pcmBuffer = new byte[512];
        int bufIdx = 0;

        while (enabled) {
            try {
                Short sample = sampleQueue.take();
                short val = sample;
                pcmBuffer[bufIdx] = (byte) (val & 0xFF);
                bufIdx++;
                pcmBuffer[bufIdx] = (byte) ((val >> 8) & 0xFF);
                bufIdx++;

                if (bufIdx >= pcmBuffer.length) {
                    if (audioLine != null && audioLine.isOpen()) {
                        audioLine.write(pcmBuffer, 0, bufIdx);
                    }
                    bufIdx = 0;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IllegalArgumentException | IllegalStateException e) {
                // Ignore transient audio line state/write errors
                bufIdx = 0;
            }
        }
    }

    public static boolean isAudioAccess(int address) {
        return address >= AUDIO_BASE && address < AUDIO_BASE + 0x100;
    }

    public byte readByte(int address) {
        return 0;
    }

    public int readWord(int address) {
        if (address == REG_CTRL) {
            return enabled ? 1 : 0;
        }
        if (address == REG_FIFO_FREE) {
            return sampleQueue.remainingCapacity();
        }
        if (address == REG_SAMPLE_RATE) {
            return sampleRate;
        }
        return 0;
    }

    public void writeByte(int address, byte value) {
        // No-op for byte write
    }

    public void writeWord(int address, int value) {
        if (address == REG_CTRL) {
            boolean wantEnabled = (value & 1) != 0;
            if (wantEnabled) {
                boolean threadDead = (playbackThread == null || !playbackThread.isAlive());
                if (!this.enabled || threadDead) {
                    this.enabled = true;
                    this.sampleQueue.clear();
                    initAudioSystem(sampleRate);
                }
            } else {
                this.enabled = false;
                this.sampleQueue.clear();
            }
        } else if (address == REG_FIFO_DATA) {
            if (enabled) {
                sampleQueue.offer((short) (value & 0xFFFF));
            }
        } else if (address == REG_SAMPLE_RATE) {
            int newRate = value;
            if (newRate > 0 && newRate != this.sampleRate) {
                this.sampleRate = newRate;
                initAudioSystem(newRate);
            }
        }
    }
}
