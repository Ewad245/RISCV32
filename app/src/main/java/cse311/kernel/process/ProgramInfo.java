package cse311.kernel.process;

/**
 * Holds memory layout information extracted from an ELF executable.
 */
import java.util.Collections;
import java.util.Map;

public class ProgramInfo {
    public final int entryPoint;
    public final int textStart;
    public final int textSize;
    public final int dataStart;
    public final int dataSize;
    public final int heapStart;
    public final Map<Integer, String> symbols;

    public ProgramInfo(int entryPoint, int textStart, int textSize, int dataStart, int dataSize, int heapStart,
            Map<Integer, String> symbols) {
        this.entryPoint = entryPoint;
        this.textStart = textStart;
        this.textSize = textSize;
        this.dataStart = dataStart;
        this.dataSize = dataSize;
        this.heapStart = heapStart;
        this.symbols = symbols != null ? symbols : Collections.emptyMap();
    }

    // Legacy constructor for compatibility if needed, or update callers
    public ProgramInfo(int entryPoint, int textStart, int textSize, int dataStart, int dataSize, int heapStart) {
        this(entryPoint, textStart, textSize, dataStart, dataSize, heapStart, Collections.emptyMap());
    }
}
