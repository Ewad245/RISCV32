package cse311.gui.components;

import com.techsenger.jeditermfx.core.TtyConnector;
import cse311.MemoryManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class EmulatorTtyConnector implements TtyConnector {
    private final InputStream in;
    private final MemoryManager memory;
    private boolean connected = true;

    public EmulatorTtyConnector(InputStream in, MemoryManager memory) {
        this.in = in;
        this.memory = memory;
    }

    @Override
    public void close() {
        connected = false;
        try {
            in.close();
        } catch (IOException e) {
            cse311.Logger.FileLogger.log(e);
        }
    }

    @Override
    public String getName() {
        return "RISC-V Emulator";
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        byte[] b = new byte[length];
        int n = in.read(b, 0, length);
        if (n == -1) {
            return -1;
        }
        
        // Convert ISO-8859-1 (raw bytes) or ASCII to char. We just do simple byte to char cast
        // since the emulator outputs standard ASCII including escape codes.
        for (int i = 0; i < n; i++) {
            buf[offset + i] = (char) (b[i] & 0xFF);
        }
        return n;
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        String str = new String(bytes, StandardCharsets.UTF_8);
        memory.getInput(str);
    }

    @Override
    public void write(String string) throws IOException {
        memory.getInput(string);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public int waitFor() throws InterruptedException {
        while (connected) {
            Thread.sleep(100);
        }
        return 0; // Return exit code 0 when finished
    }

    @Override
    public boolean ready() throws IOException {
        return in.available() > 0;
    }
}
