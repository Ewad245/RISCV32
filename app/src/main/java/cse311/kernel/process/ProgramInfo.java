package cse311.kernel.process;

/**
 * Holds memory layout information extracted from an ELF executable.
 */
public class ProgramInfo {
    public final int entryPoint;
    public final int textStart;
    public final int textSize;
    public final int dataStart;
    public final int dataSize;
    public final int heapStart;

    public ProgramInfo(int entryPoint, int textStart, int textSize, int dataStart, int dataSize, int heapStart) {
        this.entryPoint = entryPoint;
        this.textStart = textStart;
        this.textSize = textSize;
        this.dataStart = dataStart;
        this.dataSize = dataSize;
        this.heapStart = heapStart;
    }
}
